package com.muyu.blog.domain.enums;

public enum BookStatus {

    /**
     * 初始化：刚上传/入库，等待处理
     */
    INIT(0),
    /**
     * 处理中：AI 正在分析
     */
    PROCESSING(1),
    /**
     * 已完成：AI 分析完成
     */
    COMPLETED(2);

    private final int code;

    BookStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

