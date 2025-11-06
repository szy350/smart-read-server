package com.muyu.blog.domain;

import lombok.Data;

@Data
public class Article {

    private String title;
    private String summary;
    private String cover;
    private String content;
    private String author;
    private String status;
    private String category;
    private String createTime;
    private String updateTime;
}
