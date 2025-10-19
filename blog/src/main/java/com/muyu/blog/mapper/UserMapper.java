package com.muyu.blog.mapper;

import com.muyu.blog.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    void register(@Param("user") User user);

    User getUserByUserName(@Param("userName") String userName);
    // 定义数据库操作方法

}
