package com.muyu.blog.domain.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {

    private String userName;
    private String email;
    private String password;
}
