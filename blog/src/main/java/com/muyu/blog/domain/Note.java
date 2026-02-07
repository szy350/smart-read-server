package com.muyu.blog.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Note {
    private Long id;
    private Long userId;
    private Long bookId;
    private String content;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


