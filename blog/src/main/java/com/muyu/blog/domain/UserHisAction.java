package com.muyu.blog.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserHisAction {
    private Long id;
    private Long userId;
    private Long bookId;
    private Long noteId;
    private String bookName;
    private Long pageNo;
    private String context;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


