package com.muyu.blog.config;

import org.springframework.http.CacheControl;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源处理
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                // 开发态：强制不缓存，避免前端静态资源更新但页面仍不变
                .setCacheControl(CacheControl.noStore());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 配置根路径访问 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}

