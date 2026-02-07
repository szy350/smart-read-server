package com.muyu.blog.mapper;

import com.muyu.blog.domain.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PageMapper {
    int insert(Page page);

    List<Page> selectByBookId(@Param("bookId") Long bookId);

    Integer countByBookId(@Param("bookId") Long bookId);

    /**
     * 判断某本书的某页是否已存在（用于避免重复插入导致页数膨胀）。
     */
    Integer countByBookIdAndPageNo(@Param("bookId") Long bookId, @Param("pageNo") Long pageNo);

    Page selectByBookIdAndPageNo(@Param("bookId") Long bookId, @Param("pageNo") Long pageNo);

    int updateOcrResultByBookIdAndPageNo(@Param("bookId") Long bookId,
                                         @Param("pageNo") Long pageNo,
                                         @Param("type") String type,
                                         @Param("oriText") String oriText,
                                         @Param("annotation") String annotation,
                                         @Param("pdfOcrOriContent") String pdfOcrOriContent);

    int deleteByBookId(@Param("bookId") Long bookId);
}


