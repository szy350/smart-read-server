package com.muyu.blog.domain.response;

import com.alibaba.fastjson.JSONObject;
import com.muyu.blog.common.Constants;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CommonResponse {

    private String code;
    private String message;
    private String data;

    public static CommonResponse success() {
        return CommonResponse.builder().code(Constants.SUCCESS_CODE).build();
    }

    public static CommonResponse success(String msg, String data) {
        return CommonResponse.builder().code(Constants.SUCCESS_CODE).message(msg).data(data).build();
    }

    public static CommonResponse success(String msg, Object data) {
        return CommonResponse.builder().code(Constants.SUCCESS_CODE).message(msg).data(JSONObject.toJSONString(data))
                .build();
    }

    public static CommonResponse error(String errorCode, String msg) {
        return CommonResponse.builder().code(errorCode).message(msg).build();
    }
}
