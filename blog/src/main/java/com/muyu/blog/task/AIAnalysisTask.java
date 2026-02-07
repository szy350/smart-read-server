package com.muyu.blog.task;

import com.muyu.blog.domain.Book;
import com.muyu.blog.domain.Page;
import com.muyu.blog.domain.enums.BookStatus;
import com.muyu.blog.mapper.BookMapper;
import com.muyu.blog.mapper.PageMapper;
import com.muyu.blog.service.TencentOcrService;
import com.muyu.blog.util.TencentOcrTextFormatter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AIAnalysisTask {

    private static final int TXT_LINES_PER_PAGE = 100;

    /**
     * OCR 页数上限（0 表示不限制）。用于控制成本/调试。
     */
    @Value("${tencent.ocr.max-pages:500}")
    private int tencentOcrMaxPages;

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private PageMapper pageMapper;

    @Autowired
    private TencentOcrService tencentOcrService;

    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    public void processBooks() {
        // 1) 查询处于初始化状态的记录
        List<Book> initBooks = bookMapper.selectByStatus(BookStatus.INIT.getCode());

        if (initBooks == null || initBooks.isEmpty()) {
            return;
        }

        log.info("Found {} INIT books for analysis.", initBooks.size());

        // 2) 先将本批次记录状态更新为 PROCESSING（只有当前为 INIT 才能更新成功）
        List<Book> processingBooks = new ArrayList<>();
        for (Book book : initBooks) {
            try {
                if (book == null || book.getId() == null) {
                    continue;
                }
                int updated = bookMapper.updateStatusIfCurrent(
                        book.getId(),
                        BookStatus.PROCESSING.getCode(),
                        BookStatus.INIT.getCode()
                );
                if (updated == 1) {
                    processingBooks.add(book);
                }
            } catch (Exception e) {
                log.error("Error updating book status INIT->PROCESSING, id={}", book == null ? null : book.getId(), e);
            }
        }

        if (processingBooks.isEmpty()) {
            return;
        }

        // 3) 循环处理（具体处理逻辑先空着）
        for (Book book : processingBooks) {
            try {
                String filePath = book.getFilePath();
                if (filePath == null || filePath.isBlank()) {
                    throw new IllegalArgumentException("book.filePath 为空");
                }

                String lower = filePath.toLowerCase();
                if (lower.endsWith(".txt")) {
                    // txt：按行分页（每 100 行 1 页），写入 page 表的 ori_text
                    String content = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                    List<String> chunks = splitByLines(content, TXT_LINES_PER_PAGE);
                    long pageNo = 1L;
                    for (String chunk : chunks) {
                        Page page = new Page();
                        page.setBookId(book.getId().longValue());
                        page.setPageNo(pageNo++);
                        page.setType("txt");
                        page.setOriText(chunk);
                        Integer existed = pageMapper.countByBookIdAndPageNo(page.getBookId(), page.getPageNo());
                        if (existed != null && existed > 0) {
                            continue;
                        }
                        pageMapper.insert(page);
                    }

                    // 处理成功后，将 book 状态更新为 2（COMPLETED）
                    int updated = bookMapper.updateStatusIfCurrent(
                            book.getId(),
                            BookStatus.COMPLETED.getCode(),
                            BookStatus.PROCESSING.getCode()
                    );
                    if (updated != 1) {
                        log.warn("Inserted page but failed to update book status PROCESSING->COMPLETED, id={}", book.getId());
                    } else {
                        log.info("TXT processed successfully, book id={}, pages={}", book.getId(), Math.max(1, chunks.size()));
                    }
                } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
                    // doc/docx：抽取文本后按行分页（每 100 行 1 页），写入 page 表
                    String content;
                    if (lower.endsWith(".docx")) {
                        content = extractTextFromDocx(filePath);
                    } else {
                        content = extractTextFromDoc(filePath);
                    }

                    List<String> chunks = splitByLines(content, TXT_LINES_PER_PAGE);
                    long pageNo = 1L;
                    for (String chunk : chunks) {
                        Page page = new Page();
                        page.setBookId(book.getId().longValue());
                        page.setPageNo(pageNo++);
                        page.setType(lower.endsWith(".docx") ? "docx" : "doc");
                        page.setOriText(chunk);
                        Integer existed = pageMapper.countByBookIdAndPageNo(page.getBookId(), page.getPageNo());
                        if (existed != null && existed > 0) {
                            continue;
                        }
                        pageMapper.insert(page);
                    }

                    int updated = bookMapper.updateStatusIfCurrent(
                            book.getId(),
                            BookStatus.COMPLETED.getCode(),
                            BookStatus.PROCESSING.getCode()
                    );
                    if (updated != 1) {
                        log.warn("Inserted page but failed to update book status PROCESSING->COMPLETED, id={}", book.getId());
                    } else {
                        log.info("DOC/DOCX processed successfully, book id={}, pages={}", book.getId(), Math.max(1, chunks.size()));
                    }
                } else if (lower.endsWith(".pdf")) {
                    // pdf：先判断是否可编辑
                    boolean editable = isPdfEditable(filePath);
                    if (editable) {
                        // 可编辑 PDF：按 PDF 页码逐页抽取文本，写入 page 表
                        int pagesInserted = 0;
                        try (PDDocument doc = PDDocument.load(Path.of(filePath).toFile())) {
                            int totalPages = doc.getNumberOfPages();
                            PDFTextStripper stripper = new PDFTextStripper();
                            for (int p = 1; p <= totalPages; p++) {
                                stripper.setStartPage(p);
                                stripper.setEndPage(p);
                                String text = stripper.getText(doc);
                                if (text == null) {
                                    text = "";
                                }

                                Page page = new Page();
                                page.setBookId(book.getId().longValue());
                                page.setPageNo((long) p);
                                page.setType("pdf");
                                page.setOriText(text);
                                Integer existed = pageMapper.countByBookIdAndPageNo(page.getBookId(), page.getPageNo());
                                if (existed != null && existed > 0) {
                                    continue;
                                }
                                pageMapper.insert(page);
                                pagesInserted++;
                            }
                        }

                        int updated = bookMapper.updateStatusIfCurrent(
                                book.getId(),
                                BookStatus.COMPLETED.getCode(),
                                BookStatus.PROCESSING.getCode()
                        );
                        if (updated != 1) {
                            log.warn("Inserted page but failed to update book status PROCESSING->COMPLETED, id={}", book.getId());
                        } else {
                            log.info("PDF(text-based) processed successfully, book id={}, pages={}", book.getId(), pagesInserted);
                        }
                    } else {
                        // 不可编辑 PDF（疑似扫描件）：逐页拆分 -> base64 -> 调用腾讯云 OCR -> 写入 page 表
                        int pagesInserted = 0;
                        try (PDDocument doc = PDDocument.load(Path.of(filePath).toFile())) {
                            int totalPages = doc.getNumberOfPages();
                            if (tencentOcrMaxPages > 0) {
                                totalPages = Math.min(totalPages, tencentOcrMaxPages);
                            }
                            // todo 这里先写死，后续要改成1到totalPages
                            for (int p = 1; p <= 30; p++) {
                                long bookId = book.getId().longValue();
                                long pageNo = (long) p;
                                String pagePdfBase64 = extractSinglePagePdfAsBase64(doc, p);
                                // 传单页 PDF 时，页码固定为 1
                                String resp = tencentOcrService.generalAccurateOcrPdfBase64(pagePdfBase64, 1);
                                String ocrText = extractDetectedTextFromTencentOcrResp(resp);
                                String[] bodyAndAnnotation = splitBodyAndAnnotation(ocrText);
                                String oriText = bodyAndAnnotation[0];
                                String annotation = bodyAndAnnotation[1];

                                // 每页都重新 OCR：已存在则更新，否则插入
                                int updated = pageMapper.updateOcrResultByBookIdAndPageNo(
                                        bookId, pageNo, "pdf", oriText, annotation, resp
                                );
                                if (updated <= 0) {
                                    Page page = new Page();
                                    page.setBookId(bookId);
                                    page.setPageNo(pageNo);
                                    page.setType("pdf");
                                    page.setOriText(oriText);
                                    page.setAnnotation(annotation);
                                    // 保存每页 OCR 原始 JSON 返回，便于回溯/二次处理
                                    page.setPdfOcrOriContent(resp);
                                    pageMapper.insert(page);
                                    pagesInserted++;
                                }

                                // 返回可能较大，这里只记录页码 + 字符数，避免刷爆日志
                                log.info("Tencent OCR done, book id={}, page={}/{}, textLen={}",
                                        book.getId(), p, totalPages, ocrText == null ? 0 : ocrText.length());
                            }
                        }

                        int updated = bookMapper.updateStatusIfCurrent(
                                book.getId(),
                                BookStatus.COMPLETED.getCode(),
                                BookStatus.PROCESSING.getCode()
                        );
                        if (updated != 1) {
                            log.warn("Inserted page but failed to update book status PROCESSING->COMPLETED, id={}", book.getId());
                        } else {
                            log.info("PDF(OCR) processed successfully, book id={}, pages={}", book.getId(), pagesInserted);
                        }
                    }
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    // 图片：直接走腾讯 OCR（GeneralAccurateOCR），保存为 1 页
                    long bookId = book.getId().longValue();
                    long pageNo = 1L;
                    byte[] bytes = Files.readAllBytes(Path.of(filePath));
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    String mime = lower.endsWith(".png") ? "image/png" : "image/jpeg";
                    String resp = tencentOcrService.generalAccurateOcrImageBase64(base64, mime);
                    String ocrText = extractDetectedTextFromTencentOcrResp(resp);
                    String[] bodyAndAnnotation = splitBodyAndAnnotation(ocrText);
                    String oriText = bodyAndAnnotation[0];
                    String annotation = bodyAndAnnotation[1];

                    String type = lower.endsWith(".png") ? "png" : "jpg";
                    int updated = pageMapper.updateOcrResultByBookIdAndPageNo(
                            bookId, pageNo, type, oriText, annotation, resp
                    );
                    if (updated <= 0) {
                        Page page = new Page();
                        page.setBookId(bookId);
                        page.setPageNo(pageNo);
                        page.setType(type);
                        page.setOriText(oriText);
                        page.setAnnotation(annotation);
                        // 复用字段保存 OCR 原始 JSON（字段名历史原因）
                        page.setPdfOcrOriContent(resp);
                        pageMapper.insert(page);
                    }

                    int bookUpdated = bookMapper.updateStatusIfCurrent(
                            book.getId(),
                            BookStatus.COMPLETED.getCode(),
                            BookStatus.PROCESSING.getCode()
                    );
                    if (bookUpdated != 1) {
                        log.warn("Inserted page but failed to update book status PROCESSING->COMPLETED, id={}", book.getId());
                    } else {
                        log.info("IMAGE(OCR) processed successfully, book id={}, textLen={}", book.getId(), ocrText == null ? 0 : ocrText.length());
                    }
                } else {
                    log.warn("Unsupported file type, book id={}, filePath={}", book.getId(), filePath);
                }
            } catch (Exception e) {
                log.error("Error processing book id={}", book.getId(), e);
                try {
                    // 失败回滚到 INIT，便于下次重试
                    bookMapper.updateStatusIfCurrent(
                            book.getId(),
                            BookStatus.INIT.getCode(),
                            BookStatus.PROCESSING.getCode()
                    );
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    private boolean isPdfEditable(String filePath) {
        try (PDDocument doc = PDDocument.load(Path.of(filePath).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(1, doc.getNumberOfPages()));
            String text = stripper.getText(doc);
            if (text == null) {
                return false;
            }
            // 非空且含一定长度的可见字符，视为可编辑（文本型）
            String normalized = text.replaceAll("\\s+", "");
            return normalized.length() >= 10;
        } catch (Exception e) {
            log.warn("Failed to detect PDF editable, treat as non-editable. filePath={}, err={}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 从一个已加载的 PDF 文档中，拆分出指定页（1-based）并返回该“单页 PDF”的 base64（不含 data: 前缀）。
     * 注意：拆分后是单页 PDF，因此 OCR 请求里 PdfPageNumber 固定传 1 即可。
     */
    private String extractSinglePagePdfAsBase64(PDDocument doc, int pageNo) throws Exception {
        if (doc == null) return "";
        int total = doc.getNumberOfPages();
        if (total <= 0) return "";
        if (pageNo < 1 || pageNo > total) return "";

        Splitter splitter = new Splitter();
        splitter.setStartPage(pageNo);
        splitter.setEndPage(pageNo);
        List<PDDocument> pages = splitter.split(doc);
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        PDDocument single = pages.get(0);
        try (single; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            single.save(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } finally {
            for (int i = 1; i < pages.size(); i++) {
                try {
                    pages.get(i).close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * 从腾讯 OCR 响应中提取文本（按行拼接）。若解析失败，则返回原始响应（避免丢信息）。
     */
    private String extractDetectedTextFromTencentOcrResp(String resp) {
        return TencentOcrTextFormatter.formatFromTencentOcrResp(resp);
    }

    /** 与 TencentOcrTextFormatter 中【注释】标记一致，用于拆分正文与注释 */
    private static final String ANNOTATION_MARKER = "\n\n【注释】\n";

    /**
     * 将 OCR 格式化后的全文拆分为正文与注释。
     * 若存在「【注释】」段则拆开，否则注释为 null。
     *
     * @return [0]=正文 oriText, [1]=注释 annotation（可能为 null）
     */
    private static String[] splitBodyAndAnnotation(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return new String[]{ocrText, null};
        }
        int idx = ocrText.indexOf(ANNOTATION_MARKER);
        if (idx < 0) {
            return new String[]{ocrText.strip(), null};
        }
        String body = ocrText.substring(0, idx).strip();
        String annotation = ocrText.substring(idx + ANNOTATION_MARKER.length()).strip();
        return new String[]{body, annotation.isEmpty() ? null : annotation};
    }

    /**
     * 将文本按“行数”切分为多段，每段最多 linesPerPage 行。
     * 说明：这里把“行”定义为按换行符切分后的逻辑行，避免依赖渲染引擎的分页结果。
     */
    private static List<String> splitByLines(String content, int linesPerPage) {
        if (linesPerPage <= 0) {
            throw new IllegalArgumentException("linesPerPage must be > 0");
        }
        if (content == null || content.isBlank()) {
            return List.of("");
        }

        // 统一换行符并保留空行
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);

        List<String> chunks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int lineCnt = 0;
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) {
                sb.append('\n');
            }
            lineCnt++;
            if (lineCnt >= linesPerPage) {
                chunks.add(sb.toString());
                sb.setLength(0);
                lineCnt = 0;
            }
        }
        if (sb.length() > 0 || chunks.isEmpty()) {
            chunks.add(sb.toString());
        }
        return chunks;
    }

    private static String extractTextFromDocx(String filePath) throws Exception {
        try (InputStream is = Files.newInputStream(Path.of(filePath));
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            return text == null ? "" : text;
        }
    }

    private static String extractTextFromDoc(String filePath) throws Exception {
        try (InputStream is = Files.newInputStream(Path.of(filePath));
             HWPFDocument doc = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(doc)) {
            String text = extractor.getText();
            return text == null ? "" : text;
        }
    }
}

