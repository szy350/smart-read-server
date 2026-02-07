package com.muyu.blog.mapper;

import com.muyu.blog.domain.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookMapper {

    void insert(Book book);

    void insertBatch(@Param("books") List<Book> books);
    List<Book> selectByUsernameAndFilename(@Param("userName") String userName, @Param("fileName") String fileName);
    String selectProcessingFileName(@Param("userName") String userName);
    List<String> selectProcessingFileNames(@Param("userName") String userName);
    Integer countByUserIdAndFileName(@Param("userId") Long userId, @Param("fileName") String fileName);
    List<Book> selectByStatus(@Param("status") Integer status);
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    int updateStatusIfCurrent(@Param("id") Integer id,
                              @Param("toStatus") Integer toStatus,
                              @Param("fromStatus") Integer fromStatus);

    List<Book> selectCompletedByUserName(@Param("userName") String userName, @Param("status") Integer status);

    Book selectByIdAndUserName(@Param("id") Long id, @Param("userName") String userName);

    int deleteByIdAndUserName(@Param("id") Long id, @Param("userName") String userName);
}

