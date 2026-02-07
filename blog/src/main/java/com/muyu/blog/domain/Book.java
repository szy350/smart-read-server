package com.muyu.blog.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Book {
    private Integer id;
    private Long userId;
    private String userName;
    private String fileName;
    private String filePath;
    private Long status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

