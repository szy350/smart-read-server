package com.muyu.blog.domain;

import lombok.Data;

@Data
public class User {
    private String userName;
    private String email;
    private String password;
    private Boolean admin;
}
