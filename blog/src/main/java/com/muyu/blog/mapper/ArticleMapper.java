package com.muyu.blog.mapper;

import com.muyu.blog.domain.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleMapper {

    List<Article> getArticleList(@Param("begin") int begin, @Param("size") int size);

    int getArticleCount();
}
