package com.muyu.blog.controller;

import com.muyu.blog.common.Constants;
import com.muyu.blog.domain.User;
import com.muyu.blog.domain.UserHisAction;
import com.muyu.blog.domain.response.CommonResponse;
import com.muyu.blog.mapper.UserHisActionMapper;
import com.muyu.blog.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class UserHisActionController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserHisActionMapper userHisActionMapper;

    @PostMapping("/user/his-action/get")
    public CommonResponse get(@RequestParam("userName") String userName) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            UserHisAction action = userHisActionMapper.selectLatestByUserId(user.getId());
            return CommonResponse.success("获取成功", action);
        } catch (Exception e) {
            log.error("get user his-action error, userName={}", userName, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取失败");
        }
    }

    @PostMapping("/user/his-action/save")
    public CommonResponse save(@RequestParam("userName") String userName,
                               @RequestParam(value = "bookId", required = false) Long bookId,
                               @RequestParam(value = "noteId", required = false) Long noteId,
                               @RequestParam(value = "bookName", required = false) String bookName,
                               @RequestParam(value = "pageNo", required = false) Long pageNo,
                               @RequestParam(value = "context", required = false) String context) {
        if (StringUtils.isBlank(userName)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            Long userId = user.getId();

            UserHisAction existing = userHisActionMapper.selectLatestByUserId(userId);

            UserHisAction action = new UserHisAction();
            action.setUserId(userId);
            action.setBookId(bookId);
            action.setNoteId(noteId);
            action.setBookName(bookName);
            action.setPageNo(pageNo);
            action.setContext(context);

            if (existing == null || existing.getId() == null) {
                userHisActionMapper.insert(action);
            } else {
                userHisActionMapper.updateByUserId(action);
            }
            return CommonResponse.success("已记录", null);
        } catch (Exception e) {
            log.error("save user his-action error, userName={}, bookId={}, noteId={}, pageNo={}", userName, bookId, noteId, pageNo, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "记录失败");
        }
    }
}


