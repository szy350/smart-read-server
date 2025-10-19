package com.muyu.blog.service;

import com.muyu.blog.domain.User;

public interface UserService {

    String register(User user);

    String login(User user);
}
