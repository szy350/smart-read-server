package com.muyu.blog.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 尝试根据腾讯 OCR（GeneralAccurateOCR/GeneralBasicOCR）返回的坐标信息，尽量恢复文本格式。
 *
 * <p>核心策略：</p>
 * <ul>
 *   <li>按 Y（行中心）聚类为“行”</li>
 *   <li>行内按 X 排序</li>
 *   <li>根据框间距推断空格；根据行间距推断空行</li>
 *   <li>对明显的页码/页眉页脚做轻量过滤</li>
 * </ul>
 *
 * <p>注意：OCR 天然无法 100% 还原原始排版，本工具偏向“阅读友好”。</p>
 */
public final class TencentOcrTextFormatter {

    private TencentOcrTextFormatter() {}

    private static final Pattern FOOTER_LIKE =
            // 兼容：·vii·、- 1 -、.1、_12_ 等
            Pattern.compile("^\\s*[·•\\-—_\\.]*\\s*(\\d+|[ivxlcdmIVXLCDM]+)\\s*[·•\\-—_\\.]*\\s*$");

    // 右下角页脚：作者 | 书名（如“金耀基 | 中国社会与文化”），仅用于底部区域过滤
    private static final Pattern BOOK_TITLE_FOOTER =
            Pattern.compile("^[\\s\\p{IsHan}A-Za-z0-9_·－—\\-]+\\s*[|｜]\\s*[\\s\\p{IsHan}A-Za-z0-9_·－—\\-《》（）\\(\\)]+$");

    /** 行内“作者|书名”子串（与页码等同行时需剥离），避免第8页等出现页脚残留 */
    private static final Pattern BOOK_TITLE_FOOTER_SUBSTRING =
            Pattern.compile("[\\p{IsHan}A-Za-z0-9_·－—\\-\\s]{2,25}\\s*[|｜]\\s*[\\s\\p{IsHan}A-Za-z0-9_·－—\\-《》（）\\.]{2,35}");

    /** 页脚/章标题：“增訂版導言”或“增 訂 版 導 ⾔”（OCR 可能带空格），一律剔除 */
    private static final Pattern REVISED_EDITION_INTRO_FOOTER =
            Pattern.compile("增\\s*訂\\s*版\\s*導\\s*言");

    // 正文里的脚注标记：紧跟在标点/右引号/右括号/右角括号（〉》）后面的单个数字（1-9）
    private static final Pattern INLINE_FOOTNOTE_MARKER =
            // 兼容：。11、。 11（OCR 有时会插空格）；这里限制为 1-2 位，避免年份/页码误判
            // 允许出现在行末/空白/常见标点前（比如 “。 14” 在行末）
            Pattern.compile("([。．.!?！？；;:：，,、」』》）〉》\\)\\]】])\\s*([1-9]\\d?)(?=(\\s|$|[\\p{IsHan}A-Za-z]|[。．.!?！？；;:：，,、」』》）〉》\\)\\]】]))");

    private static final char[] SUPERSCRIPTS = {
            '⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹'
    };

    private static final Pattern FOOTNOTE_START =
            Pattern.compile("^\\s*(\\d{1,2})\\s*(?=\\S)");

    /** 强特征：数字+参/见/参见+作者（如「2参金耀基:」），即使不在页面底部、缩进不明显也视为注释起始 */
    private static final Pattern CITATION_NUM_REF =
            Pattern.compile("^\\s*\\d{1,2}\\s*(参|見|见|参见|參見)\\s*\\S");

    /** 行内“正文+注释起始”拆分：匹配（可选空格+）数字+空格 后接 参/见 或 中文名+英文字母（如「 5 金耀基A」「及有關5 金耀基A」） */
    private static final Pattern INLINE_FOOTNOTE_START =
            Pattern.compile("\\s*(\\d{1,2})\\s+(?=(参|見|见|参见|參見|\\p{IsHan}{2,15}[A-Za-z]))");

    /** 单行仅为章节号（一、二、…、十）时可与下一行合并，避免“二”单独成行错乱 */
    private static boolean isSectionNumberOnlyLine(String s) {
        if (s == null) return false;
        String t = s.strip();
        if (t.isEmpty()) return false;
        if (t.length() == 1) return "一二三四五六七八九十".indexOf(t.charAt(0)) >= 0;
        if (t.length() == 2) return "一二三四五六七八九十".indexOf(t.charAt(0)) >= 0 && t.charAt(1) == '、';
        return false;
    }

    /**
     * 参考文献/引文常见特征（英文作者、编辑、出版社、页码、年份等）。
     * 说明：这里只做“偏保守”的启发式识别，并结合缩进/字号等版式信号降低误判。
     */
    private static final Pattern YEAR_LIKE = Pattern.compile("\\b(18|19|20)\\d{2}\\b");
    private static final Pattern CITATION_KEYWORDS = Pattern.compile(
            "(?i)\\b(pp\\.|p\\.|ed\\.|vol\\.|no\\.|university|press|institutions|chicago|honolulu|middlesex|penguin)\\b"
    );

    public static String formatFromTencentOcrResp(String resp) {
        if (resp == null || resp.isBlank()) return "";
        try {
            JSONObject root = JSON.parseObject(resp);
            if (root == null) return "";
            JSONObject response = getObjectByTrimmedKey(root, "Response");
            if (response == null) return "";
            JSONObject err = getObjectByTrimmedKey(response, "Error");
            if (err != null) {
                String code = err.getString("Code");
                String msg = err.getString("Message");
                return "【OCR错误】" + (code == null ? "" : code) + (msg == null ? "" : (": " + msg));
            }

            JSONArray detections = getArrayByTrimmedKey(response, "TextDetections");
            if (detections == null || detections.isEmpty()) return "";

            List<Box> boxes = new ArrayList<>(detections.size());
            for (int i = 0; i < detections.size(); i++) {
                JSONObject item = detections.getJSONObject(i);
                if (item == null) continue;
                String text = item.getString("DetectedText");
                if (text == null) text = "";
                text = text.strip();
                if (text.isEmpty()) continue;

                JSONObject poly = item.getJSONObject("ItemPolygon");
                int x = poly == null ? 0 : safeInt(poly, "X");
                int y = poly == null ? 0 : safeInt(poly, "Y");
                int w = poly == null ? 0 : safeInt(poly, "Width");
                int h = poly == null ? 0 : safeInt(poly, "Height");
                int paragNo = parseParagNo(item.getString("AdvancedInfo"));

                // 若 ItemPolygon 缺失，尝试用 Polygon 兜底（取 min/max）
                if ((w <= 0 || h <= 0) && item.getJSONArray("Polygon") != null) {
                    int[] rect = rectFromPolygon(item.getJSONArray("Polygon"));
                    if (rect != null) {
                        x = rect[0];
                        y = rect[1];
                        w = rect[2];
                        h = rect[3];
                    }
                }

                boxes.add(new Box(text, x, y, w, h, paragNo));
            }

            if (boxes.isEmpty()) return "";
            boxes.sort((a, b) -> {
                int c = Integer.compare(a.yCenter(), b.yCenter());
                if (c != 0) return c;
                return Integer.compare(a.x, b.x);
            });

            int medianH = medianHeight(boxes);
            int yThreshold = Math.max(4, (int) Math.round(medianH * 0.60));
            int blankLineThreshold = Math.max(medianH + 6, (int) Math.round(medianH * 1.60));
            int indentThreshold = Math.max(18, (int) Math.round(medianH * 1.20));
            // 注释区常用较小缩进（约 1 字宽），单独阈值，避免 42px 等被漏判导致续行/无编号引文进正文
            int footnoteIndentThreshold = Math.max(14, (int) Math.round(medianH * 0.70));
            int spaceThreshold = Math.max(10, (int) Math.round(medianH * 0.85));

            int globalMinX = boxes.stream().mapToInt(b -> b.x).min().orElse(0);
            int pageMaxY = boxes.stream().mapToInt(b -> b.y + Math.max(0, b.h)).max().orElse(0);

            List<Line> lines = toLines(boxes, yThreshold);
            // 行内排序 + 行属性
            for (Line line : lines) {
                line.items.sort((a, b) -> Integer.compare(a.x, b.x));
                line.minX = line.items.get(0).x;
                line.yCenter = line.items.get(0).yCenter(); // 近似
                line.paragNo = dominantParagNo(line.items);
                line.medianH = medianHeight(line.items);
            }

            List<String> bodyLines = new ArrayList<>();
            List<String> footnoteLines = new ArrayList<>();

            int prevY = 0;
            boolean hasPrev = false;
            int prevParagNo = -1;
            boolean inNoteBlock = false;
            int prevNoteY = 0;
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                boolean indented = (line.minX - globalMinX) >= indentThreshold;
                boolean indentedForFootnote = (line.minX - globalMinX) >= footnoteIndentThreshold;
                String lineText = buildLineText(line.items, globalMinX, indentThreshold, spaceThreshold);
                if (lineText.isBlank()) continue;

                // 行内剥离已知页脚子串（如“金耀基|中國社會與文化”“增 訂 版 導 ⾔”），避免与页码同行时漏过滤
                lineText = stripKnownFooters(lineText);
                if (lineText.isBlank() || FOOTER_LIKE.matcher(lineText.strip()).matches()) {
                    continue;
                }

                // 页码/页眉页脚：只在“靠近页面底部”时才过滤，避免误伤脚注编号
                if (looksLikeFooterOrHeader(lineText, line.yCenter, pageMaxY)) {
                    continue;
                }

                // 通用：行内“正文+注释起始”拆分（如「及有關  5 金耀基Ambrose...」），避免整行被当正文导致注释跑上去
                String[] split = trySplitBodyAndFootnoteStart(lineText);
                if (split != null && !split[0].isEmpty()) {
                    if (hasPrev) {
                        int yGap = prevY > 0 ? Math.abs(line.yCenter - prevY) : 0;
                        if (yGap >= blankLineThreshold) bodyLines.add("");
                    }
                    bodyLines.add(split[0]);
                    prevY = line.yCenter;
                    hasPrev = true;
                    prevParagNo = line.paragNo;
                    lineText = split[1];
                }

                // 注释/引文区识别：
                // 1) 强特征：数字+参/见+作者（如「2参金耀基:」）-> 一律视为注释起始（适配第7页等）
                // 2) 常规脚注：页面偏底部 + 小字号/编号
                // 3) 参考文献/引文块：缩进 +（英文引文特征/编号）-> 视为注释块起始
                // 4) 进入注释块后：后续缩进行若不以新编号开头则归入同一条（一条注释多行/多段出版信息）
                // 5) 明显是正文的长句不归入注释；明显是章节标题（如「增訂版導言」）则结束注释块
                boolean isNoteStart = isFootnoteStartLine(lineText, line, pageMaxY, medianH, indented, indentedForFootnote);
                boolean startsWithNewFootnoteNum = startsWithFootnoteNumber(lineText);
                boolean looksLikeNoteTail = looksLikeFootnoteContinuation(lineText);
                int maxNoteGap = looksLikeNoteTail
                        ? (int) Math.round(blankLineThreshold * 2.2)
                        : (int) Math.round(blankLineThreshold * 1.20);
                boolean isNoteContinue = inNoteBlock
                        && indentedForFootnote
                        && prevNoteY > 0
                        && Math.abs(line.yCenter - prevNoteY) <= maxNoteGap
                        && !looksLikeSectionTitle(lineText)
                        && (/* 不以新编号开头则一律续行 */ !startsWithNewFootnoteNum || !looksLikeBodyParagraph(lineText, line, pageMaxY));
                if (isNoteStart || isNoteContinue) {
                    inNoteBlock = true;
                    prevNoteY = line.yCenter;
                    footnoteLines.add(normalizeFootnoteLeadNumber(lineText.strip()));
                    continue;
                } else {
                    inNoteBlock = false;
                    prevNoteY = 0;
                }

                // 正文：把“标点后面的单个数字”视为脚注标记 -> 上标，避免曲解为正文数字
                lineText = normalizeInlineFootnoteMarkers(lineText);

                // 去除行首或行尾的页码（如"·viii·"）
                lineText = removePageNumberFromLine(lineText);
                if (lineText.isBlank()) continue;

                // 正文分段（保留原逻辑）
                if (hasPrev) {
                    int yGap = Math.abs(line.yCenter - prevY);
                    if (yGap >= blankLineThreshold) {
                        bodyLines.add(""); // 空行
                    } else if (line.paragNo != -1 && prevParagNo != -1 && line.paragNo != prevParagNo && indented) {
                        bodyLines.add(""); // 空行
                    }
                }
                bodyLines.add(lineText);
                prevY = line.yCenter;
                hasPrev = true;
                prevParagNo = line.paragNo;
            }

            StringBuilder out = new StringBuilder();
            for (int i = 0; i < bodyLines.size(); i++) {
                if (i > 0) out.append('\n');
                out.append(bodyLines.get(i));
            }

            if (!footnoteLines.isEmpty()) {
                out.append("\n\n【注释】\n");
                String prevNo = null;
                for (String ln : footnoteLines) {
                    if (ln == null) continue;
                    String t = ln.strip();
                    if (t.isEmpty()) continue;
                    var m = FOOTNOTE_START.matcher(t);
                    String curNo = m.find() ? m.group(1) : null;
                    if (prevNo != null && curNo != null && !curNo.equals(prevNo)) {
                        out.append('\n');
                    }
                    out.append(t).append('\n');
                    if (curNo != null) prevNo = curNo;
                }
                // 去掉末尾多余空行
                return out.toString().strip();
            }

            return out.toString().strip();
        } catch (Exception e) {
            // 解析失败则兜底：直接返回原始响应（避免丢信息）
            return resp;
        }
    }

    private static boolean looksLikeFooterOrHeader(String s, int yCenter, int pageMaxY) {
        if (s == null) return false;
        String t = s.strip();
        if (t.isEmpty()) return false;
        // “增訂版導言”/“增 訂 版 導 ⾔”一律视为页脚，不输出（与位置无关）
        if ("增訂版導言".equals(t.replaceAll("\\s+", ""))) {
            return true;
        }
        // 仅当靠近底部才视为页码/页脚（避免误伤脚注编号）
        if (pageMaxY > 0 && yCenter < (int) Math.round(pageMaxY * 0.72)) {
            return false;
        }
        // 纯页码行（·vii·、- 1 - 等）
        if (t.length() <= 12 && FOOTER_LIKE.matcher(t).matches()) {
            return true;
        }
        // 右下角“作者 | 书名”类页脚（如“金耀基 | 中国社会与文化”）
        return looksLikeBookTitleFooter(t);
    }

    /** 识别右下角“作者 | 书名”形式的页脚，仅按内容判断（调用方已保证在底部区域） */
    private static boolean looksLikeBookTitleFooter(String t) {
        if (t == null || t.length() < 4 || t.length() > 35) return false;
        if (!t.contains("|") && !t.contains("｜")) return false;
        // 格式：左侧作者名 + | 或 ｜ + 右侧书名，以中文或常见字符为主
        return BOOK_TITLE_FOOTER.matcher(t).matches();
    }

    /**
     * 若行内含“正文+注释起始”（如「及有關  5 金耀基Ambrose...」），拆成 [正文部分, 注释部分]；
     * 否则返回 null。用于避免正文与注释因 Y 聚类合并成一行时整行被当正文。
     */
    private static String[] trySplitBodyAndFootnoteStart(String line) {
        if (line == null || line.length() < 6) return null;
        Matcher m = INLINE_FOOTNOTE_START.matcher(line);
        if (!m.find()) return null;
        int splitAt = m.start();
        String bodyPart = line.substring(0, splitAt).trim();
        String footnotePart = line.substring(splitAt).replaceFirst("^\\s+", "").trim();
        if (footnotePart.length() < 3) return null;
        return new String[] { bodyPart, footnotePart };
    }

    /**
     * 从行内剥离已知页脚子串（与页码/其他内容同行时也会被剔除），避免第8页等出现“金耀基|中國社會與文化”或“增 訂 版 導 ⾔”残留。
     * 剥离后若整行为空或仅剩页码，调用方应整行跳过。
     */
    private static String stripKnownFooters(String line) {
        if (line == null || line.isEmpty()) return line;
        String t = line;
        t = REVISED_EDITION_INTRO_FOOTER.matcher(t).replaceAll("");
        t = BOOK_TITLE_FOOTER_SUBSTRING.matcher(t).replaceAll("");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean isFootnoteLine(String lineText, Line line, int pageMaxY, int medianH) {
        if (lineText == null) return false;
        if (pageMaxY <= 0) return false;
        // 底部区域（经验阈值）：脚注通常在页面下 40% 区域
        if (line.yCenter < (int) Math.round(pageMaxY * 0.60)) {
            return false;
        }
        int lh = line.medianH > 0 ? line.medianH : medianH;
        boolean smallFont = lh > 0 && lh <= (int) Math.round(medianH * 0.92);
        String t = lineText.strip();
        boolean startsWithNum = FOOTNOTE_START.matcher(t).find();
        // 小字号 或 以“编号”开头，认为是脚注区
        return smallFont || startsWithNum;
    }

    /** 明显是正文的长句（以中文为主）：不当作注释，避免“增訂版《中國社會與文化》的十四篇論文…”等被误判到注释区（与是否在页面底部无关） */
    private static boolean looksLikeBodyParagraph(String lineText, Line line, int pageMaxY) {
        if (lineText == null || lineText.length() < 15) return false;
        String t = lineText.strip();
        if (t.length() < 15) return false;
        // 长句且以中文为主（ASCII 占比低）即视为正文，不因在页面底部就判成注释
        if (asciiRatio(t) < 0.20) {
            return true;
        }
        return false;
    }

    private static boolean isFootnoteStartLine(String lineText, Line line, int pageMaxY, int medianH,
                                              boolean indented, boolean indentedForFootnote) {
        if (lineText == null) return false;
        String t = lineText.strip();
        if (t.isEmpty()) return false;

        // 0) 强特征：数字+参/见/参见+作者（如「2参金耀基:」），一律视为注释起始，不依赖底部/缩进
        if (CITATION_NUM_REF.matcher(t).find()) {
            return true;
        }

        // 1) 仅中文章节号（一、二、…、十）单独一行时视为正文标题，不是脚注，避免“二”被误判为注释
        if (isSectionNumberOnlyLine(t)) {
            return false;
        }

        // 2) 明显是正文的长句（以中文为主）不判为注释，避免“增訂版《中國社會與文化》的十四篇論文…”被误判
        if (looksLikeBodyParagraph(lineText, line, pageMaxY)) {
            return false;
        }

        // 3) 明确的“注释”标题
        if ((t.startsWith("【") || t.startsWith("[") || t.startsWith("（") || t.startsWith("("))
                && t.contains("注释")) {
            return true;
        }

        int lh = line.medianH > 0 ? line.medianH : medianH;
        boolean smallFont = lh > 0 && lh <= (int) Math.round(medianH * 0.92);
        boolean startsWithNum = FOOTNOTE_START.matcher(t).find();

        // 4) 常规脚注：底部区域 +（小字号/编号）；且必须是“数字编号”开头，避免单字“二”仅因小字号被当脚注
        if (startsWithNum && isFootnoteLine(t, line, pageMaxY, medianH)) {
            return true;
        }

        // 4.5) 通用：编号开头 + 一定英文占比（如「5 金耀基Ambrose...」首行与正文对齐、无缩进），即使不在底部也视为注释起始
        if (startsWithNum && asciiRatio(t) >= 0.25) {
            return true;
        }

        // 5) 引文/参考文献块：缩进 +（引文特征/编号）+（小字号或明显英文占比）；用注释缩进阈值，兼容小缩进版式
        if (indentedForFootnote && (startsWithNum || looksLikeCitation(t))) {
            if (smallFont) return true;
            return asciiRatio(t) >= 0.35; // 给英文引文更强权重
        }

        // 6) 通用：缩进 + 高英文占比 + 引文特征（如「Chinese Culture and Mental Health, ed. by ...」），无编号也视为注释起始
        if (indentedForFootnote && asciiRatio(t) >= 0.50 && looksLikeCitation(t)) {
            return true;
        }

        // 7) 非缩进情况下的编号脚注（例如：版式没缩进但字号很小）
        return startsWithNum && smallFont;
    }

    /** 行首是否为脚注编号（1-2位数字后跟非空白），用于判断是否为新一条注释 */
    private static boolean startsWithFootnoteNumber(String lineText) {
        if (lineText == null || lineText.isBlank()) return false;
        return FOOTNOTE_START.matcher(lineText.strip()).find();
    }

    /** 短句且无引文特征，疑为章节标题（如「增訂版導言」），应结束注释块、不归入上一条注释。
     * 明显为注释/引文续行的内容（如「究》，香港:牛津大學出版社，2013。」）不当作章节标题，避免被拆到正文。 */
    private static boolean looksLikeSectionTitle(String lineText) {
        if (lineText == null) return false;
        String t = lineText.strip();
        if (t.length() < 3 || t.length() > 20) return false;
        if (startsWithFootnoteNumber(t)) return false;
        if (looksLikeCitation(t)) return false;
        // 注释续行常见：含出版社/出版、年份、以》/）开头、以。结尾 → 不当作章节标题
        if (t.contains("出版社") || t.contains("出版")) return false;
        if (YEAR_LIKE.matcher(t).find()) return false;
        if (t.endsWith("。")) return false;
        if (t.startsWith("》") || t.startsWith("）") || t.startsWith("〉")) return false;
        return asciiRatio(t) < 0.30;
    }

    /** 明显为注释/引文尾行（中英文通用），用于放宽续行 Y 距离 */
    private static boolean looksLikeFootnoteContinuation(String lineText) {
        if (lineText == null || lineText.isBlank()) return false;
        String t = lineText.strip();
        if (t.contains("出版社") || t.contains("出版")) return true;
        if (YEAR_LIKE.matcher(t).find()) return true;
        if (t.endsWith("。")) return true;
        if (t.startsWith("》") || t.startsWith("）") || t.startsWith("〉")) return true;
        // 英文引文续行：(New York: Academic Press, 1985). pp. 29-46.
        if (t.startsWith("(") && (t.contains("Press") || t.contains("pp.") || YEAR_LIKE.matcher(t).find())) return true;
        if (CITATION_KEYWORDS.matcher(t).find() && asciiRatio(t) >= 0.40) return true;
        return false;
    }

    private static boolean looksLikeCitation(String s) {
        if (s == null) return false;
        String t = s.strip();
        if (t.isEmpty()) return false;

        // 常见中文引文引导
        if (t.startsWith("见") || t.startsWith("見") || t.startsWith("参见") || t.startsWith("參見")) {
            return true;
        }
        // 常见英文引文引导
        if (t.regionMatches(true, 0, "see", 0, 3)) {
            return true;
        }
        // 强特征 token
        if (CITATION_KEYWORDS.matcher(t).find()) {
            return true;
        }
        if (t.contains("pp.") || t.contains("ed.") || t.contains("Press") || t.contains("University")) {
            return true;
        }
        // 年份 + 英文占比较高
        if (YEAR_LIKE.matcher(t).find() && asciiRatio(t) >= 0.35) {
            return true;
        }
        // 英文占比很高 + 括号/逗号（出版社/城市/年份格式常见）
        double r = asciiRatio(t);
        if (r >= 0.55 && (t.contains("(") || t.contains(")") || t.contains(",") || t.contains("，"))) {
            return true;
        }
        return false;
    }

    private static double asciiRatio(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        int ascii = 0;
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) continue;
            total++;
            // 把“英文字母/数字/常见英文标点”视作 ascii
            if (c <= 127 && (Character.isLetterOrDigit(c) || ",.;:'\"()[]{}-/—_".indexOf(c) >= 0)) {
                ascii++;
            }
        }
        if (total == 0) return 0.0;
        return (double) ascii / (double) total;
    }

    private static String normalizeInlineFootnoteMarkers(String s) {
        if (s == null || s.isBlank()) return s;
        var m = INLINE_FOOTNOTE_MARKER.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String p = m.group(1);
            String digits = m.group(2);
            String sup = toSuperscriptDigits(digits);
            m.appendReplacement(sb, Matcher.quoteReplacement(p + sup));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 去除行首或行尾的页码模式（如"·viii·"）。
     * 只去除明显的页码格式，避免误伤正文中的罗马数字。
     */
    private static String removePageNumberFromLine(String s) {
        if (s == null || s.isBlank()) return s;
        String t = s.strip();
        if (t.isEmpty()) return s;

        // 去除行首的页码：·viii·、·viii· 文本、-viii- 文本 等
        // 匹配格式：分隔符 + 罗马数字 + 分隔符 + 可选空格
        t = t.replaceFirst("^\\s*[·•\\-—_\\.]+\\s*[ivxlcdmIVXLCDM]+\\s*[·•\\-—_\\.]+\\s*", "");
        
        // 去除行尾的页码：文本 ·viii· 等
        t = t.replaceFirst("\\s*[·•\\-—_\\.]+\\s*[ivxlcdmIVXLCDM]+\\s*[·•\\-—_\\.]+\\s*$", "");

        // 如果去除后只剩下空白，返回空字符串
        t = t.strip();
        return t.isEmpty() ? "" : t;
    }

    /**
     * 注释行常见格式：编号紧跟内容（如 9Ruey...），补一个空格更易读。
     */
    private static String normalizeFootnoteLeadNumber(String s) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return t;
        // OCR 可能把脚注号拆成多个框，导致行首出现 "1 4 Chinese..." 这种：先把行首数字合并
        // 仅在“行首两位数字 + 空格 + 非数字内容”时合并，避免误伤正文
        t = t.replaceFirst("^(\\d)\\s+(\\d)(?=\\s*[^\\d\\s])", "$1$2");
        // 已经有空格就不动
        var m = Pattern.compile("^(\\d{1,3})(\\S)").matcher(t);
        if (m.find()) {
            return m.replaceFirst("$1 $2");
        }
        return t;
    }

    private static String toSuperscriptDigits(String digits) {
        if (digits == null || digits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(digits.length());
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(SUPERSCRIPTS[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String buildLineText(List<Box> items, int globalMinX, int indentThreshold, int spaceThreshold) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        int minX = items.get(0).x;
        // “首行缩进”近似：若该行明显右移，则加两个空格
        if (minX - globalMinX >= indentThreshold) {
            sb.append("  ");
        }

        for (int i = 0; i < items.size(); i++) {
            Box cur = items.get(i);
            if (i > 0) {
                Box prev = items.get(i - 1);
                int prevRight = prev.x + Math.max(0, prev.w);
                int gap = cur.x - prevRight;
                if (gap >= spaceThreshold) {
                    // gap 很大时，多给一点空格（兼容两列/表格）
                    sb.append(gap >= spaceThreshold * 2 ? "  " : " ");
                }
            }
            sb.append(cur.text);
        }
        return sb.toString().stripTrailing();
    }

    private static List<Line> toLines(List<Box> sortedByY, int yThreshold) {
        List<Line> lines = new ArrayList<>();
        Line cur = null;
        int baseY = 0;
        boolean hasBaseY = false;
        for (Box b : sortedByY) {
            int yc = b.yCenter();
            if (cur == null) {
                cur = new Line();
                cur.items.add(b);
                baseY = yc;
                hasBaseY = true;
                continue;
            }
            if (hasBaseY && Math.abs(yc - baseY) <= yThreshold) {
                cur.items.add(b);
            } else {
                lines.add(cur);
                cur = new Line();
                cur.items.add(b);
                baseY = yc;
                hasBaseY = true;
            }
        }
        if (cur != null) lines.add(cur);
        return lines;
    }

    private static int dominantParagNo(List<Box> items) {
        if (items == null || items.isEmpty()) return -1;
        // 同一行通常 paragNo 相同；这里取“第一个非 -1”即可（足够稳定）
        for (Box b : items) {
            if (b != null && b.paragNo != -1) return b.paragNo;
        }
        return -1;
    }

    private static int medianHeight(List<Box> boxes) {
        List<Integer> hs = new ArrayList<>();
        for (Box b : boxes) {
            if (b.h > 0) hs.add(b.h);
        }
        if (hs.isEmpty()) return 24;
        hs.sort(Integer::compareTo);
        return hs.get(hs.size() / 2);
    }

    private static int parseParagNo(String advancedInfo) {
        if (advancedInfo == null || advancedInfo.isBlank()) return -1;
        try {
            JSONObject adv = JSON.parseObject(advancedInfo);
            if (adv == null) return -1;
            JSONObject parag = adv.getJSONObject("Parag");
            if (parag == null) return -1;
            Integer n = parag.getInteger("ParagNo");
            return n == null ? -1 : n;
        } catch (Exception ignore) {
            return -1;
        }
    }

    private static int safeInt(JSONObject obj, String key) {
        if (obj == null || key == null) return 0;
        Integer v = obj.getInteger(key);
        return v == null ? 0 : v;
    }

    private static int[] rectFromPolygon(JSONArray polygon) {
        if (polygon == null || polygon.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < polygon.size(); i++) {
            JSONObject p = polygon.getJSONObject(i);
            if (p == null) continue;
            int x = safeInt(p, "X");
            int y = safeInt(p, "Y");
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (minX == Integer.MAX_VALUE) return null;
        return new int[] { minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY) };
    }

    private static JSONObject getObjectByTrimmedKey(JSONObject obj, String target) {
        if (obj == null || target == null) return null;
        JSONObject direct = obj.getJSONObject(target);
        if (direct != null) return direct;
        for (String k : obj.keySet()) {
            if (k != null && k.trim().equals(target)) {
                Object v = obj.get(k);
                if (v instanceof JSONObject) return (JSONObject) v;
                if (v instanceof String) {
                    try { return JSON.parseObject((String) v); } catch (Exception ignore) { return null; }
                }
            }
        }
        return null;
    }

    private static JSONArray getArrayByTrimmedKey(JSONObject obj, String target) {
        if (obj == null || target == null) return null;
        JSONArray direct = obj.getJSONArray(target);
        if (direct != null) return direct;
        for (String k : obj.keySet()) {
            if (k != null && k.trim().equals(target)) {
                Object v = obj.get(k);
                if (v instanceof JSONArray) return (JSONArray) v;
                if (v instanceof String) {
                    try { return JSON.parseArray((String) v); } catch (Exception ignore) { return null; }
                }
            }
        }
        return null;
    }

    private static final class Line {
        final List<Box> items = new ArrayList<>();
        int minX;
        int yCenter;
        int paragNo = -1;
        int medianH;
    }

    private static final class Box {
        final String text;
        final int x;
        final int y;
        final int w;
        final int h;
        final int paragNo;

        Box(String text, int x, int y, int w, int h, int paragNo) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.paragNo = paragNo;
        }

        int yCenter() {
            return y + Math.max(0, h) / 2;
        }
    }
}


