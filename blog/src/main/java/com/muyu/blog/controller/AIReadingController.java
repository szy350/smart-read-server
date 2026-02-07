package com.muyu.blog.controller;

import com.muyu.blog.common.Constants;
import com.muyu.blog.domain.Book;
import com.muyu.blog.domain.Page;
import com.muyu.blog.domain.enums.BookStatus;
import com.muyu.blog.domain.User;
import com.muyu.blog.domain.response.CommonResponse;
import com.muyu.blog.mapper.BookMapper;
import com.muyu.blog.mapper.NoteMapper;
import com.muyu.blog.mapper.PageMapper;
import com.muyu.blog.mapper.UserMapper;
import com.muyu.blog.service.AIReadingService;
import com.muyu.blog.util.TencentOcrTextFormatter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class AIReadingController {

    @Autowired
    private AIReadingService aiReadingService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private PageMapper pageMapper;

    @Autowired
    private NoteMapper noteMapper;

    @PostMapping("/ai-reading/upload")
    public CommonResponse upload(@RequestParam("userName") String userName,
                                 @RequestParam("file") MultipartFile file) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户名不能为空");
        }
        if (file == null || file.isEmpty()) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "上传文件不能为空");
        }

        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            if (user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户ID缺失，无法保存数据");
            }

            String fileName = file.getOriginalFilename();
            if (StringUtils.isBlank(fileName)) {
                fileName = file.getName();
            }

            String lower = fileName == null ? "" : fileName.toLowerCase();
            if (!(lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".pdf")
                    || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "仅支持上传 txt、doc、docx、pdf、png、jpg 格式文件");
            }

            // 同一用户不允许上传同名文件（book表已存在记录则拒绝）
            if (aiReadingService.existsBookFileName(user.getId(), fileName)) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "该文件名已存在，请修改文件名后再上传");
            }

            aiReadingService.saveBook(user.getId(), user.getUserName(), file);
            return CommonResponse.success("上传成功", null);
        } catch (Exception e) {
            log.error("upload ai reading file error, userName={}", userName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "文件上传处理失败");
        }
    }

    @PostMapping("/ai-reading/progress")
    public CommonResponse getProgress(@RequestParam("userName") String userName,
                                      @RequestParam("fileName") String fileName) {
        if (StringUtils.isBlank(userName) || StringUtils.isBlank(fileName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            Double progress = aiReadingService.getParsingProgress(userName, fileName);
            return CommonResponse.success("获取成功", new AIReadingService.ProcessingFileResponse(fileName, progress));
        } catch (Exception e) {
            log.error("get parsing progress error, userName={}, fileName={}", userName, fileName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取进度失败");
        }
    }

    @PostMapping("/ai-reading/check-processing")
    public CommonResponse checkProcessing(@RequestParam("userName") String userName) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户名不能为空");
        }
        try {
            AIReadingService.ProcessingFileResponse response = aiReadingService.getProcessingFile(userName);
            return CommonResponse.success("获取成功", response);
        } catch (Exception e) {
            log.error("check processing file error, userName={}", userName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "检查正在解析的文件失败");
        }
    }

    @PostMapping("/ai-reading/check-processing-list")
    public CommonResponse checkProcessingList(@RequestParam("userName") String userName) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户名不能为空");
        }
        try {
            List<AIReadingService.ProcessingFileResponse> responses = aiReadingService.getProcessingFiles(userName);
            return CommonResponse.success("获取成功", responses);
        } catch (Exception e) {
            log.error("check processing file list error, userName={}", userName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "检查正在解析的文件列表失败");
        }
    }

    /**
     * 选取文件时的前置校验：同一用户不允许上传同名文件
     */
    @PostMapping("/ai-reading/check-duplicate")
    public CommonResponse checkDuplicate(@RequestParam("userName") String userName,
                                         @RequestParam("fileName") String fileName) {
        if (StringUtils.isBlank(userName) || StringUtils.isBlank(fileName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            boolean exists = aiReadingService.existsBookFileName(user.getId(), fileName);
            Map<String, Object> data = new HashMap<>();
            data.put("exists", exists);
            return CommonResponse.success("获取成功", data);
        } catch (Exception e) {
            log.error("check duplicate file error, userName={}, fileName={}", userName, fileName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "检查文件是否重复失败");
        }
    }

    /**
     * 我的书架：仅返回当前用户已解析完成的书籍（不返回 filePath 等敏感字段）
     */
    @PostMapping("/ai-reading/books")
    public CommonResponse listCompletedBooks(@RequestParam("userName") String userName) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户名不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            List<Book> books = bookMapper.selectCompletedByUserName(userName, BookStatus.COMPLETED.getCode());
            List<Map<String, Object>> data = new ArrayList<>();
            if (books != null) {
                for (Book b : books) {
                    if (b == null || b.getId() == null) {
                        continue;
                    }
                    Integer pageCount = pageMapper.countByBookId(b.getId().longValue());
                    Map<String, Object> item = new HashMap<>();
                    item.put("bookId", b.getId());
                    item.put("fileName", b.getFileName());
                    item.put("status", b.getStatus());
                    item.put("createTime", b.getCreateTime());
                    item.put("updateTime", b.getUpdateTime());
                    item.put("pageCount", pageCount == null ? 0 : pageCount);
                    data.add(item);
                }
            }
            return CommonResponse.success("获取成功", data);
        } catch (Exception e) {
            log.error("list completed books error, userName={}", userName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取书架失败");
        }
    }

    /**
     * 读取书籍分页：仅允许读取自己的书；当前先返回 oriText（mdContent 后续填充再扩展）
     */
    @PostMapping("/ai-reading/book/pages")
    public CommonResponse getBookPages(@RequestParam("userName") String userName,
                                       @RequestParam("bookId") Long bookId) {
        if (StringUtils.isBlank(userName) || bookId == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            // 权限校验：book 必须属于该 userName
            Book book = bookMapper.selectByIdAndUserName(bookId, userName);
            if (book == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍不存在或无权限访问");
            }
            if (book.getStatus() == null || book.getStatus() != BookStatus.COMPLETED.getCode()) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍尚未解析完成");
            }

            List<Page> pages = pageMapper.selectByBookId(bookId);
            List<Map<String, Object>> data = new ArrayList<>();
            if (pages != null) {
                for (Page p : pages) {
                    if (p == null) continue;
                    Map<String, Object> item = new HashMap<>();
                    item.put("pageNo", p.getPageNo());
                    item.put("type", p.getType());
                    item.put("oriText", extractPageText(p));
                    data.add(item);
                }
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("bookId", book.getId());
            resp.put("fileName", book.getFileName());
            resp.put("pages", data);
            return CommonResponse.success("获取成功", resp);
        } catch (Exception e) {
            log.error("get book pages error, userName={}, bookId={}", userName, bookId, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取书籍内容失败");
        }
    }

    /**
     * 按页读取：仅返回指定 pageNo 的内容 + 总页数（用于前端按需加载/跳页）。
     */
    @PostMapping("/ai-reading/book/page")
    public CommonResponse getBookPage(@RequestParam("userName") String userName,
                                      @RequestParam("bookId") Long bookId,
                                      @RequestParam("pageNo") Long pageNo) {
        if (StringUtils.isBlank(userName) || bookId == null || pageNo == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        if (pageNo <= 0) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "页码必须大于 0");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            Book book = bookMapper.selectByIdAndUserName(bookId, userName);
            if (book == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍不存在或无权限访问");
            }
            if (book.getStatus() == null || book.getStatus() != BookStatus.COMPLETED.getCode()) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍尚未解析完成");
            }

            Integer totalPages = pageMapper.countByBookId(bookId);
            Page page = pageMapper.selectByBookIdAndPageNo(bookId, pageNo);
            if (page == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "该页不存在");
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("bookId", book.getId());
            resp.put("fileName", book.getFileName());
            resp.put("totalPages", totalPages == null ? 0 : totalPages);
            resp.put("pageNo", page.getPageNo());
            resp.put("type", page.getType());
            resp.put("oriText", extractPageText(page));
            return CommonResponse.success("获取成功", resp);
        } catch (Exception e) {
            log.error("get book page error, userName={}, bookId={}, pageNo={}", userName, bookId, pageNo, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取书籍内容失败");
        }
    }

    /**
     * 书架右侧 AI 提问：基于当前页（或指定页）的 oriText 作为上下文进行问答。
     * - 前端建议传 userName、bookId、pageNo、question
     * - AI Key 等配置暂时允许为空，未配置时返回友好提示
     */
    @PostMapping("/ai-reading/ask")
    public CommonResponse ask(@RequestParam("userName") String userName,
                              @RequestParam("bookId") Long bookId,
                              @RequestParam(value = "pageNo", required = false) Long pageNo,
                              @RequestParam("question") String question,
                              @RequestParam(value = "context", required = false) String context) {
        if (StringUtils.isBlank(userName) || bookId == null || StringUtils.isBlank(question)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        Long effectivePageNo = (pageNo == null || pageNo <= 0) ? 1L : pageNo;
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            Book book = bookMapper.selectByIdAndUserName(bookId, userName);
            if (book == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍不存在或无权限访问");
            }
            if (book.getStatus() == null || book.getStatus() != BookStatus.COMPLETED.getCode()) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍尚未解析完成");
            }

            Page page = pageMapper.selectByBookIdAndPageNo(bookId, effectivePageNo);
            if (page == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "该页不存在");
            }

            // 上下文优先级：
            // 1) 前端显式传入 context（比如选中文本/手动拼接）
            // 2) 当前页内容：mdContent -> oriText -> pdfOcrOriContent
            // 3) 若当前页为空：拼接当前页附近若干页（默认 ±2 页）
            String finalContext = StringUtils.isNotBlank(context) ? context : buildContextFromPageOrNearby(bookId, effectivePageNo, page);
            if (StringUtils.isBlank(finalContext)) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "未找到可用正文上下文（该页内容为空）");
            }

            String answer = aiReadingService.askQuestion(question, finalContext);
            Map<String, Object> resp = new HashMap<>();
            resp.put("bookId", bookId);
            resp.put("fileName", book.getFileName());
            resp.put("pageNo", effectivePageNo);
            resp.put("question", question);
            resp.put("answer", answer);
            resp.put("contextLen", finalContext.length());
            return CommonResponse.success("获取成功", resp);
        } catch (Exception e) {
            log.error("ai ask error, userName={}, bookId={}, pageNo={}", userName, bookId, effectivePageNo, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "AI问答失败");
        }
    }

    /**
     * 删除书籍（不可恢复）：物理删除 book/page/note 三张表的数据。
     * - 仅允许删除自己的书
     * - 删除顺序：note -> page -> book
     */
    @PostMapping("/ai-reading/book/delete")
    @Transactional(rollbackFor = Exception.class)
    public CommonResponse deleteBook(@RequestParam("userName") String userName,
                                     @RequestParam("bookId") Long bookId) {
        if (StringUtils.isBlank(userName) || bookId == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            if (Boolean.TRUE.equals(user.getAdmin())) {
                return CommonResponse.error(Constants.PERMISSION_ERROR_CODE, "用户没有权限");
            }

            Book book = bookMapper.selectByIdAndUserName(bookId, userName);
            if (book == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "书籍不存在或无权限删除");
            }

            int noteDeleted = noteMapper.deleteByUserIdAndBookId(user.getId(), bookId);
            int pageDeleted = pageMapper.deleteByBookId(bookId);
            int bookDeleted = bookMapper.deleteByIdAndUserName(bookId, userName);
            if (bookDeleted <= 0) {
                // 理论上不会发生（前面已查到 book），兜底抛异常触发事务回滚
                throw new IllegalStateException("delete book failed, bookId=" + bookId);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("bookId", bookId);
            resp.put("fileName", book.getFileName());
            resp.put("noteDeleted", noteDeleted);
            resp.put("pageDeleted", pageDeleted);
            resp.put("bookDeleted", bookDeleted);
            return CommonResponse.success("删除成功", resp);
        } catch (Exception e) {
            // 事务内吞异常会导致不回滚：这里显式标记回滚
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ignore) {
                // ignore
            }
            log.error("delete book error, userName={}, bookId={}", userName, bookId, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "删除失败");
        }
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) return null;
        for (String s : candidates) {
            if (StringUtils.isNotBlank(s)) return s;
        }
        return null;
    }

    private String extractPageText(Page p) {
        if (p == null) return null;
        // 优先使用更“结构化/清洗过”的内容
        String md = p.getMdContent();
        if (StringUtils.isNotBlank(md)) return md;

        String raw = p.getPdfOcrOriContent();
        // 若存在腾讯 OCR 原始 JSON，则用最新规则实时格式化，保证“注释/正文”分段的修复立即生效
        if (looksLikeTencentOcrJson(raw)) {
            try {
                String formatted = TencentOcrTextFormatter.formatFromTencentOcrResp(raw);
                if (StringUtils.isNotBlank(formatted)) {
                    return formatted;
                }
            } catch (Exception ignore) {
                // ignore
            }
        }

        return firstNonBlank(p.getOriText(), raw);
    }

    private boolean looksLikeTencentOcrJson(String s) {
        if (StringUtils.isBlank(s)) return false;
        // 轻量判断：避免把非 OCR JSON 的内容送进 formatter
        return s.contains("\"TextDetections\"") && s.contains("\"Response\"");
    }

    private String buildContextFromPageOrNearby(Long bookId, Long pageNo, Page currentPage) {
        // 先用当前页
        String current = extractPageText(currentPage);
        if (StringUtils.isNotBlank(current)) {
            return current;
        }

        // 当前页为空：拼接附近页（默认 ±2 页），并做简单长度保护
        final int window = 2;
        final int maxChars = 12000;
        List<Page> pages = pageMapper.selectByBookId(bookId);
        if (pages == null || pages.isEmpty()) return null;

        long pn = pageNo == null ? 1L : pageNo;
        long start = Math.max(1L, pn - window);
        long end = pn + window;

        StringBuilder sb = new StringBuilder();
        for (Page p : pages) {
            if (p == null || p.getPageNo() == null) continue;
            long n = p.getPageNo();
            if (n < start || n > end) continue;
            String text = extractPageText(p);
            if (StringUtils.isBlank(text)) continue;
            if (sb.length() > 0) sb.append("\n\n---\n\n");
            sb.append("第").append(n).append("页：\n").append(text);
            if (sb.length() >= maxChars) {
                break;
            }
        }
        if (sb.length() == 0) return null;
        // 截断到 maxChars，避免 prompt 过长
        if (sb.length() > maxChars) {
            return sb.substring(0, maxChars);
        }
        return sb.toString();
    }
}

