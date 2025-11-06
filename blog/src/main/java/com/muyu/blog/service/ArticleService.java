package com.muyu.blog.service;

import java.util.List;

import com.muyu.blog.domain.Article;
import com.muyu.blog.domain.request.PageRequest;

public interface ArticleService {

    List<Article> getArticleList(PageRequest pageRequest);

    int getArticleCount();
}
