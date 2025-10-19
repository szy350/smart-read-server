package com.muyu.blog.domain.mapper;

import com.muyu.blog.domain.User;
import com.muyu.blog.domain.request.UserRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserRegisterMapper {
    
    UserRegisterMapper INSTANCE = Mappers.getMapper(UserRegisterMapper.class);
    
    User toUser(UserRequest userRequest);
}
