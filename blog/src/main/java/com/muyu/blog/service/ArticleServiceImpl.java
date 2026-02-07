package com.muyu.blog.service;

import com.muyu.blog.domain.Article;
import com.muyu.blog.domain.request.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.muyu.blog.mapper.ArticleMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {


    @Autowired
    private ArticleMapper articleMapper;
    
    // 按照pageNum和pageSize分页获取文章列表
    public List<Article> getArticleList(PageRequest pageRequest) {
        // 计算起始位置
        int begin = (pageRequest.getPageNum() - 1) * pageRequest.getPageSize();
        List<Article> articleList = articleMapper.getArticleList(begin, pageRequest.getPageSize());
        
        // 遍历文章列表，将 cover 字段从文件名转换为 Base64 图片数据
        return articleList.stream().map(article -> {
            if (article.getCover() != null && !article.getCover().isEmpty()) {
                try {
                    // 从 resources/cover 目录读取图片文件
                    String imagePath = "cover/" + article.getCover();
                    ClassPathResource resource = new ClassPathResource(imagePath);
                    
                    // 在jar包中，exists()可能不准确，直接尝试读取
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.getInputStream();
                        byte[] imageBytes = inputStream.readAllBytes();
                        
                        // 将图片转换为 Base64 编码
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                        
                        // 根据文件扩展名确定 MIME 类型
                        String mimeType = "image/png";
                        String fileName = article.getCover().toLowerCase();
                        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                            mimeType = "image/jpeg";
                        } else if (fileName.endsWith(".gif")) {
                            mimeType = "image/gif";
                        } else if (fileName.endsWith(".webp")) {
                            mimeType = "image/webp";
                        }
                        
                        // 设置 Data URL 格式的图片数据
                        article.setCover("data:" + mimeType + ";base64," + base64Image);
                        log.debug("Successfully loaded image: {}", imagePath);
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                log.warn("Failed to close input stream for {}", imagePath, e);
                            }
                        }
                    }
                } catch (IOException e) {
                    // 如果读取失败，记录日志并保持原文件名
                    log.error("Failed to load image: cover/{}", article.getCover(), e);
                }
            }
            return article;
        }).collect(Collectors.toList());
    }

    // 获取文章总数
    @Override
    public int getArticleCount() {
        return articleMapper.getArticleCount();
    }
}
