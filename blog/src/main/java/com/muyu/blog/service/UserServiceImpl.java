package com.muyu.blog.service;

import com.muyu.blog.domain.User;
import com.muyu.blog.mapper.UserMapper;
import com.muyu.blog.util.PasswordEncoderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoderUtil passwordEncoderUtil;

    @Override
    public String register(User user) {
        try {
            // 对用户信息进行校验
            if (user == null) {
                return "用户信息不能为空";
            }
            if (StringUtils.isEmpty(user.getUserName())) {
                return "用户名不能为空";
            }
            if (StringUtils.isEmpty(user.getEmail())) {
                return "邮箱不能为空";
            }
            if (StringUtils.isEmpty(user.getPassword())) {
                return "密码不能为空";
            }

            if (userExist(user.getUserName())) {
                return "当前用户名已存在";
            }

            // 这里需要对password加密存储
            String encodedPassword = passwordEncoderUtil.encode(user.getPassword());
            user.setPassword(encodedPassword);
            user.setAdmin(false);
            userMapper.register(user);
            log.info("success register user {}", user.getUserName());
            return null;
        } catch (Exception e) {
            log.error("register user error", e);
            return "注册发生异常失败，请留言给管理员";
        }
    }

    @Override
    public String login(User user) {
        try {
            if (user == null) {
                return "用户信息不能为空";
            }
            if (StringUtils.isEmpty(user.getUserName())) {
                return "用户名不能为空";
            }
            if (StringUtils.isEmpty(user.getPassword())) {
                return "密码不能为空";
            }
            User userDB = userMapper.getUserByUserName(user.getUserName());
            if (userDB == null) {
                return "用户不存在";
            }
            if (!passwordEncoderUtil.matches(user.getPassword(), userDB.getPassword())) {
                return "密码错误";
            }
            log.info("success login user {}", user.getUserName());
            return null;
        } catch (Exception e) {
            log.error("login user error", e);
            return "登录发生异常失败，请留言给管理员";
        }
    }

    private boolean userExist(String userName) {
        return userMapper.getUserByUserName(userName) != null;
    }
}
