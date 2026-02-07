package com.muyu.blog.mapper;

import com.muyu.blog.domain.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteMapper {

    int insert(Note note);

    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("bookId") Long bookId,
                      @Param("content") String content,
                      @Param("summary") String summary);

    List<Note> selectTopByUserIdAndBookIdOrderByUpdateTimeDesc(@Param("userId") Long userId,
                                                               @Param("bookId") Long bookId,
                                                               @Param("limit") Integer limit);

    Integer countByUserIdAndBookId(@Param("userId") Long userId,
                                   @Param("bookId") Long bookId);

    int deleteOldestOne(@Param("userId") Long userId,
                        @Param("bookId") Long bookId);

    Note selectByIdAndUserIdAndBookId(@Param("id") Long id,
                                      @Param("userId") Long userId,
                                      @Param("bookId") Long bookId);

    int updateSummary(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("bookId") Long bookId,
                      @Param("summary") String summary);

    int deleteByUserIdAndBookId(@Param("userId") Long userId,
                                @Param("bookId") Long bookId);
}


