package com.muyu.blog.service;

import com.muyu.blog.common.Constants;
import com.muyu.blog.domain.Book;
import com.muyu.blog.domain.enums.BookStatus;
import com.muyu.blog.mapper.BookMapper;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AIReadingServiceImpl implements AIReadingService {

    private static final Logger log = LoggerFactory.getLogger(AIReadingServiceImpl.class);

    /**
     * Gemini（google-genai）配置：先在 application.properties 留空，后续你填入即可。
     */
    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${ai.gemini.temperature:0.7}")
    private double geminiTemperature;

    /**
     * Gemini 调用的容错配置：用于处理 503/UNAVAILABLE/429 等暂时性错误。
     */
    @Value("${ai.gemini.retry.max-attempts:3}")
    private int geminiRetryMaxAttempts;

    @Value("${ai.gemini.retry.base-delay-ms:600}")
    private long geminiRetryBaseDelayMs;

    @Value("${ai.gemini.retry.max-delay-ms:6000}")
    private long geminiRetryMaxDelayMs;

    @Value("${ai.gemini.request-timeout-seconds:30}")
    private int geminiRequestTimeoutSeconds;

    /**
     * 备选模型：当主模型 503/UNAVAILABLE 时自动切换（逗号分隔）。
     * 例：ai.gemini.fallback-models=gemini-2.0-flash,gemini-1.5-flash
     */
    @Value("${ai.gemini.fallback-models:}")
    private String geminiFallbackModels;

    /**
     * Gemini HTTP 代理（可选）：在 application.properties 里先留空，后续你填入即可。
     * 注意：这里全部用 String 接收，避免出现“配置项留空导致 Spring 解析端口失败”的问题。
     */
    @Value("${ai.gemini.proxy.host:}")
    private String geminiProxyHost;

    @Value("${ai.gemini.proxy.port:}")
    private String geminiProxyPort;

    @Value("${ai.gemini.proxy.username:}")
    private String geminiProxyUsername;

    @Value("${ai.gemini.proxy.password:}")
    private String geminiProxyPassword;

    @Autowired
    private BookMapper bookMapper;

    private static final Random JITTER_RANDOM = new Random();

    private static String normalizeDbFileName(String originalFileName) {
        String dbFileName = originalFileName;
        if (dbFileName != null && dbFileName.length() > 100) {
            dbFileName = dbFileName.substring(0, 100);
        }
        return dbFileName;
    }

    @Override
    public boolean existsBookFileName(Long userId, String originalFileName) {
        if (userId == null || originalFileName == null || originalFileName.isBlank()) {
            return false;
        }
        String dbFileName = normalizeDbFileName(originalFileName);
        Integer cnt = bookMapper.countByUserIdAndFileName(userId, dbFileName);
        return cnt != null && cnt > 0;
    }

    @Override
    public void saveBook(Long userId, String userName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = file.getName();
        }

        String ext = "";
        int dot = originalFileName.lastIndexOf('.');
        if (dot >= 0 && dot < originalFileName.length() - 1) {
            ext = originalFileName.substring(dot);
            if (ext.length() > 10) {
                ext = "";
            }
        }

        String storedName = System.currentTimeMillis()
                + "_"
                + UUID.randomUUID().toString().replace("-", "")
                + ext;

        Path dir = Paths.get(Constants.AI_READING_BOOK_DIR);
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now();
        Book book = new Book();
        book.setUserId(userId);
        book.setUserName(userName);
        String dbFileName = normalizeDbFileName(originalFileName);
        String dbFilePath = target.toAbsolutePath().toString();
        if (dbFilePath.length() > 100) {
            dbFilePath = dbFilePath.substring(0, 100);
        }
        book.setFileName(dbFileName);
        book.setFilePath(dbFilePath);
        book.setStatus((long) BookStatus.INIT.getCode());
        book.setCreateTime(now);
        book.setUpdateTime(now);
        bookMapper.insert(book);
    }

    @Override
    public Double getParsingProgress(String userName, String fileName) {
        List<Book> books = bookMapper.selectByUsernameAndFilename(userName, fileName);
        if (books == null || books.isEmpty()) {
            return 0.0;
        }
        Book latest = books.get(0);
        if (latest.getStatus() == null) {
            return 0.0;
        }
        if (latest.getStatus() == BookStatus.COMPLETED.getCode()) {
            return 100.0;
        }
        if (latest.getStatus() == BookStatus.PROCESSING.getCode()) {
            return 50.0;
        }
        return 0.0;
    }

    @Override
    public ProcessingFileResponse getProcessingFile(String userName) {
        String fileName = bookMapper.selectProcessingFileName(userName);
        if (fileName == null) {
            return null;
        }
        Double progress = getParsingProgress(userName, fileName);
        return new ProcessingFileResponse(fileName, progress);
    }

    @Override
    public List<ProcessingFileResponse> getProcessingFiles(String userName) {
        List<String> fileNames = bookMapper.selectProcessingFileNames(userName);
        if (fileNames == null || fileNames.isEmpty()) {
            return List.of();
        }
        return fileNames.stream()
                .filter(fn -> fn != null && !fn.isBlank())
                .map(fn -> new ProcessingFileResponse(fn, getParsingProgress(userName, fn)))
                .collect(Collectors.toList());
    }

    @Override
    public String analyzeContent(String content) {
        String ctx = content == null ? "" : content.trim();
        if (ctx.isBlank()) {
            return "内容不能为空";
        }
        return askGemini(
                "你是一个阅读助手，请对以下文本做简洁摘要（要点列表，中文）：\n\n" + ctx
        );
    }

    @Override
    public String askQuestion(String question, String context) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "AI 问答未配置：请在 application.properties 配置 ai.gemini.api-key（当前留空是正常的）";
        }
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) {
            return "问题不能为空";
        }
        String ctx = context == null ? "" : context.trim();
        if (ctx.isBlank()) {
            // 允许无上下文提问，但提示模型范围
            ctx = "（无正文上下文）";
        }
        return askGemini(
                "请基于我提供的“正文上下文”回答问题，当正文上下文不够解决问题的时候，通过其他的各种渠道尝试解决。\n\n" + "正文上下文：\n" + ctx + "\n\n问题：\n" + q
        );
    }

    @Override
    public String summarize(String content, int maxChars) {
        int limit = maxChars <= 0 ? 200 : maxChars;
        String ctx = content == null ? "" : content.trim();
        if (ctx.isBlank()) {
            return "内容不能为空";
        }
        String prompt = "请将以下“笔记内容”总结为一段中文摘要，要求："
                + "1）不超过" + limit + "字；"
                + "2）不要使用列表/标题/Markdown；"
                + "3）只输出摘要正文。\n\n"
                + "笔记内容：\n"
                + ctx;
        String out = askGemini(prompt);
        if (out == null) return "";
        out = out.trim();
        // 兜底截断，避免模型偶尔超出字数
        if (out.length() > limit) {
            out = out.substring(0, limit);
        }
        return out;
    }

    private String askGemini(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "AI 未配置：请在 application.properties 配置 ai.gemini.api-key（当前留空是正常的）";
        }
        String p = prompt == null ? "" : prompt.trim();
        if (p.isBlank()) {
            return "请求内容不能为空";
        }
        try {
            HttpClient client = buildGeminiHttpClient();
            List<String> models = buildCandidateModels();

            String lastNonRetryableError = null;
            String lastRetryableHint = null;

            for (String model : models) {
                int attempts = Math.max(1, geminiRetryMaxAttempts);
                for (int attempt = 1; attempt <= attempts; attempt++) {
                    HttpResponse<String> resp = null;
                    int status = -1;
                    String body = null;
                    try {
                        resp = client.send(buildGeminiRequest(model, p), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        status = resp.statusCode();
                        body = resp.body();

                        if (status >= 200 && status < 300) {
                            String text = extractGeminiText(body);
                            if (text == null || text.isBlank()) {
                                return "AI 返回为空";
                            }
                            return text.trim();
                        }

                        // 非 2xx：判断是否可重试
                        GeminiErrorInfo err = parseGeminiError(body);
                        if (isRetryableGeminiError(status, err)) {
                            lastRetryableHint = friendlyRetryableMessage(status, err);
                            if (attempt < attempts) {
                                sleepBackoff(attempt);
                                continue;
                            } else {
                                // 当前模型重试耗尽：切换下一个模型（如果有）
                                break;
                            }
                        } else {
                            lastNonRetryableError = friendlyNonRetryableMessage(status, err);
                            // 非可重试错误：直接返回
                            return lastNonRetryableError;
                        }
                    } catch (Exception e) {
                        // 网络/超时：按可重试处理
                        lastRetryableHint = "AI 网络繁忙，请稍后重试";
                        if (attempt < attempts) {
                            sleepBackoff(attempt);
                            continue;
                        } else {
                            break;
                        }
                    }
                }
            }

            // 所有模型都失败：给出友好提示（不把原始 JSON 直接回传给前端）
            if (lastRetryableHint != null && !lastRetryableHint.isBlank()) {
                return lastRetryableHint + "（模型繁忙，已自动重试）";
            }
            if (lastNonRetryableError != null && !lastNonRetryableError.isBlank()) {
                return lastNonRetryableError;
            }
            return "AI 暂时不可用，请稍后重试";
        } catch (Exception e) {
            return "AI 调用失败：" + e.getMessage();
        }
    }

    private HttpRequest buildGeminiRequest(String model, String prompt) {
        URI uri = URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + urlEncode(model)
                        + ":generateContent?key="
                        + urlEncode(geminiApiKey)
        );
        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", geminiTemperature
                )
        );
        String body = JSON.toJSONString(payload);
        int timeoutSec = geminiRequestTimeoutSeconds <= 0 ? 30 : geminiRequestTimeoutSeconds;
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private List<String> buildCandidateModels() {
        List<String> models = new ArrayList<>();
        if (geminiModel != null && !geminiModel.trim().isBlank()) {
            models.add(geminiModel.trim());
        }
        String fb = geminiFallbackModels == null ? "" : geminiFallbackModels.trim();
        if (!fb.isBlank()) {
            String[] parts = fb.split(",");
            for (String s : parts) {
                if (s == null) continue;
                String m = s.trim();
                if (m.isBlank()) continue;
                if (!models.contains(m)) {
                    models.add(m);
                }
            }
        }
        // 兜底：至少有一个模型
        if (models.isEmpty()) {
            models.add("gemini-2.5-flash");
        }
        return models;
    }

    private void sleepBackoff(int attempt) {
        long base = Math.max(0L, geminiRetryBaseDelayMs);
        long max = Math.max(base, geminiRetryMaxDelayMs);
        // 指数退避：base * 2^(attempt-1)
        long delay = base * (1L << Math.min(10, Math.max(0, attempt - 1)));
        if (delay > max) delay = max;
        // 抖动：0~200ms，避免同一时刻集体重试
        long jitter = JITTER_RANDOM.nextInt(201);
        long sleepMs = delay + jitter;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static class GeminiErrorInfo {
        String status;   // e.g. UNAVAILABLE
        String message;  // e.g. The model is overloaded...
    }

    private static GeminiErrorInfo parseGeminiError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONObject err = root.getJSONObject("error");
            if (err == null) return null;
            GeminiErrorInfo info = new GeminiErrorInfo();
            info.status = err.getString("status");
            info.message = err.getString("message");
            return info;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean isRetryableGeminiError(int httpStatus, GeminiErrorInfo err) {
        // 典型可重试：429/500/502/503/504
        if (httpStatus == 429 || httpStatus == 500 || httpStatus == 502 || httpStatus == 503 || httpStatus == 504) {
            return true;
        }
        // Gemini 可能在 body 里给出 status=UNAVAILABLE
        String st = err == null || err.status == null ? "" : err.status.trim().toUpperCase();
        if ("UNAVAILABLE".equals(st) || "RESOURCE_EXHAUSTED".equals(st)) {
            return true;
        }
        String msg = err == null || err.message == null ? "" : err.message.toLowerCase();
        return msg.contains("overloaded") || msg.contains("try again later");
    }

    private static String friendlyRetryableMessage(int httpStatus, GeminiErrorInfo err) {
        // 对用户友好提示：不透出整段 JSON
        if (httpStatus == 429) {
            return "AI 触发限流，请稍后再试";
        }
        // 你遇到的情况
        if (httpStatus == 503 || (err != null && "UNAVAILABLE".equalsIgnoreCase(err.status))) {
            return "AI 模型繁忙，请稍后再试";
        }
        return "AI 服务繁忙，请稍后再试";
    }

    private static String friendlyNonRetryableMessage(int httpStatus, GeminiErrorInfo err) {
        String msg = err == null ? null : err.message;
        if (msg != null && msg.length() > 200) {
            msg = msg.substring(0, 200);
        }
        if (httpStatus == 407) {
            return "AI 代理鉴权失败（HTTP 407）：请检查 application.properties 中 ai.gemini.proxy.username/password 是否正确，"
                    + "或直接清空 ai.gemini.proxy.host/port 关闭代理";
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return "AI 鉴权失败：请检查 ai.gemini.api-key 是否正确";
        }
        if (httpStatus == 400) {
            return "AI 请求参数错误" + (msg == null || msg.isBlank() ? "" : ("：" + msg));
        }
        return "AI 调用失败：HTTP " + httpStatus + (msg == null || msg.isBlank() ? "" : ("，" + msg));
    }

    private HttpClient buildGeminiHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15));

        String host = geminiProxyHost == null ? "" : geminiProxyHost.trim();
        String portStr = geminiProxyPort == null ? "" : geminiProxyPort.trim();
        if (!host.isBlank() && !portStr.isBlank()) {
            try {
                int port = Integer.parseInt(portStr);
                if (port > 0 && port <= 65535) {
                    // 关键诊断信息：同 jar 本地/远端不一致时，往往是代理/配置覆盖导致
                    log.info("Gemini proxy enabled via application properties: {}:{}", host, port);
                    log.info("JVM proxy system properties: http.proxyHost={}, http.proxyPort={}, https.proxyHost={}, https.proxyPort={}",
                            System.getProperty("http.proxyHost"),
                            System.getProperty("http.proxyPort"),
                            System.getProperty("https.proxyHost"),
                            System.getProperty("https.proxyPort"));
                    builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));

                    String user = geminiProxyUsername == null ? "" : geminiProxyUsername.trim();
                    String pass = geminiProxyPassword == null ? "" : geminiProxyPassword.trim();
                    if (!user.isBlank() && !pass.isBlank()) {
                        // Java 11+ 对“HTTPS 通过代理隧道（CONNECT）使用 Basic 认证”默认可能是禁用的，
                        // 会导致同样代理参数下：curl 能用，但 Java HttpClient 返回 407。
                        // 这里在启用代理鉴权时自动放开（也可通过 JVM 参数配置，见 application.properties 注释）。
                        String tunnelingDisabled = System.getProperty("jdk.http.auth.tunneling.disabledSchemes");
                        String proxyingDisabled = System.getProperty("jdk.http.auth.proxying.disabledSchemes");
                        if (tunnelingDisabled == null || tunnelingDisabled.contains("Basic")) {
                            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                        }
                        if (proxyingDisabled == null || proxyingDisabled.contains("Basic")) {
                            System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
                        }
                        log.info("JDK http auth disabledSchemes: tunneling='{}', proxying='{}'",
                                System.getProperty("jdk.http.auth.tunneling.disabledSchemes"),
                                System.getProperty("jdk.http.auth.proxying.disabledSchemes"));

                        builder.authenticator(new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                // Java HttpClient 会在需要代理鉴权时回调这里
                                return new PasswordAuthentication(user, pass.toCharArray());
                            }
                        });
                    }
                }
            } catch (NumberFormatException ignore) {
                // 端口留空/填错时，直接按“不使用代理”处理，避免影响服务启动
            }
        }

        return builder.build();
    }

    private static String extractGeminiText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        JSONObject root = JSON.parseObject(responseBody);
        JSONArray candidates = root.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        JSONObject c0 = candidates.getJSONObject(0);
        if (c0 == null) {
            return null;
        }
        JSONObject content = c0.getJSONObject("content");
        if (content == null) {
            return null;
        }
        JSONArray parts = content.getJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        JSONObject p0 = parts.getJSONObject(0);
        return p0 == null ? null : p0.getString("text");
    }

    private static String urlEncode(String s) {
        String v = s == null ? "" : s;
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}

