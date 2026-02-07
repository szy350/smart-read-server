package com.muyu.blog.mapper;

import com.muyu.blog.domain.UserHisAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserHisActionMapper {

    UserHisAction selectLatestByUserId(@Param("userId") Long userId);

    int insert(UserHisAction action);

    int updateByUserId(UserHisAction action);
}


