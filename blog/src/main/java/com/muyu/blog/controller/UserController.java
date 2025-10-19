package com.muyu.blog.controller;

import com.muyu.blog.common.Constants;
import com.muyu.blog.domain.User;
import com.muyu.blog.domain.mapper.UserRegisterMapper;
import com.muyu.blog.domain.request.UserRequest;
import com.muyu.blog.domain.response.CommonResponse;
import com.muyu.blog.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    @ResponseBody
    public CommonResponse userRegister(@RequestBody UserRequest userRequest) {
        try {
            User user = UserRegisterMapper.INSTANCE.toUser(userRequest);
            String msg = userService.register(user);
            if (StringUtils.isEmpty(msg)) {
                return CommonResponse.success();
            }
            return CommonResponse.error(Constants.REGISTER_ERROR_CODE, msg);
        } catch (Exception e) {
            // 基本异常处理
            log.error("register user error", e);
        }
        return CommonResponse.error(Constants.REGISTER_ERROR_CODE, "注册发生异常失败，请留言给管理员");
    }

    @PostMapping("/login")
    @ResponseBody
    public CommonResponse userLogin(@RequestBody UserRequest userRequest) {
        try {
            User user = UserRegisterMapper.INSTANCE.toUser(userRequest);
            String msg = userService.login(user);
            if (StringUtils.isEmpty(msg)) {
                return CommonResponse.success();
            }
            return CommonResponse.error(Constants.LOGIN_ERROR_CODE, msg);
        } catch (Exception e) {
            // 基本异常处理
            log.error("login user error", e);
        }
        return CommonResponse.error(Constants.LOGIN_ERROR_CODE, "登录发生异常失败，请留言给管理员");
    }
}
