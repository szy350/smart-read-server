package com.muyu.blog.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Page {
    private Integer id;
    private Long bookId;
    private Long pageNo;
    private String type;
    private String oriText;
    /** 注释内容（由 OCR 格式化器识别出的脚注/引文等，与正文分离存放） */
    private String annotation;
    private String mdContent;
    private String pdfOcrOriContent;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


