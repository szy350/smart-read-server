package com.muyu.blog.common;

public interface Constants {

    String SUCCESS_CODE = "0";
    String REGISTER_ERROR_CODE = "1000";
    String LOGIN_ERROR_CODE = "1001";
    String PERMISSION_ERROR_CODE = "1002";
    String AI_READING_ERROR_CODE = "1003";

    /**
     * AI 阅读上传文件落盘目录（按需求固定到本机目录）
     */
    String AI_READING_BOOK_DIR = "/data/book";
}
