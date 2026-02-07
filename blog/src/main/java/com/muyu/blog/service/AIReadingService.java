package com.muyu.blog.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AIReadingService {

    void saveBook(Long userId, String userName, MultipartFile file);

    Double getParsingProgress(String userName, String fileName);

    ProcessingFileResponse getProcessingFile(String userName);

    List<ProcessingFileResponse> getProcessingFiles(String userName);

    boolean existsBookFileName(Long userId, String originalFileName);

    String analyzeContent(String content);

    /**
     * AI 问答：基于给定上下文回答问题。
     * - 具体大模型/供应商由实现决定（API Key 等配置从配置文件读取）。
     */
    String askQuestion(String question, String context);

    /**
     * 生成摘要（限制在指定字数以内）。
     * <p>注意：这里的“字数”按 Java 字符长度近似。</p>
     */
    String summarize(String content, int maxChars);

    @lombok.Data
    class ProcessingFileResponse {
        private String fileName;
        private Double progress;

        public ProcessingFileResponse(String fileName, Double progress) {
            this.fileName = fileName;
            this.progress = progress;
        }
    }
}

