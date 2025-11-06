package com.muyu.blog.controller;

import com.muyu.blog.domain.Article;
import com.muyu.blog.domain.request.PageRequest;
import com.muyu.blog.domain.response.CommonResponse;
import com.muyu.blog.service.ArticleService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    // 获取文章列表,需要分页
    @PostMapping("/article/list")
    public CommonResponse getArticleList(@RequestBody PageRequest pageRequest) {
        try {
            log.info("get article list request: {}", pageRequest);
            List<Article> articleList = articleService.getArticleList(pageRequest);
            return CommonResponse.success("获取文章列表成功", articleList);
        } catch (Exception e) {
            return CommonResponse.error("获取文章列表失败", e.getMessage());
        }
    }

    // 获取文章数量
    @PostMapping("/article/count")
    public CommonResponse getArticleCount() {
        try {
            int articleCount = articleService.getArticleCount();
            log.info("get article count success: {}", articleCount);
            return CommonResponse.success("获取文章数量成功", articleCount);
        } catch (Exception e) {
            return CommonResponse.error("获取文章数量失败", e.getMessage());
        }
    }

    // 获取文章详情
    // @PostMapping("/article/detail")
    // public CommonResponse getArticleDetail(@RequestBody ArticleRequest articleRequest) {
    //     try {
    //         log.info("get article detail request: {}", articleRequest);
    //         Article article = articleService.getArticleDetail(articleRequest);
    //         return CommonResponse.success("获取文章详情成功", article);
    //     } catch (Exception e) {
    //         return CommonResponse.error("获取文章详情失败", e.getMessage());
    //     }
    // }
}
