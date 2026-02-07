package com.muyu.blog.controller;

import com.muyu.blog.common.Constants;
import com.muyu.blog.domain.Note;
import com.muyu.blog.domain.User;
import com.muyu.blog.domain.response.CommonResponse;
import com.muyu.blog.mapper.NoteMapper;
import com.muyu.blog.mapper.UserMapper;
import com.muyu.blog.service.AIReadingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class NoteController {

    private static final int NOTE_LIMIT = 3;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private AIReadingService aiReadingService;

    /**
     * 获取某本书的笔记列表（最多 3 条，按 note.id 升序），默认返回第一条即“ID 最小的笔记（对应前端笔记1）”。
     */
    @PostMapping("/note/list")
    public CommonResponse list(@RequestParam("userName") String userName,
                               @RequestParam("bookId") Long bookId) {
        if (StringUtils.isBlank(userName) || bookId == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            List<Note> notes = noteMapper.selectTopByUserIdAndBookIdOrderByUpdateTimeDesc(user.getId(), bookId, NOTE_LIMIT);
            return CommonResponse.success("获取成功", notes);
        } catch (Exception e) {
            log.error("note list error, userName={}, bookId={}", userName, bookId, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "获取笔记失败");
        }
    }

    /**
     * 保存当前笔记（更新 or 新建）。
     * - noteId 传了：更新该 note
     * - noteId 不传：新建 note（超过 3 条时会自动删除最早的一条，并提示）
     */
    @PostMapping("/note/save")
    public CommonResponse save(@RequestParam("userName") String userName,
                               @RequestParam("bookId") Long bookId,
                               @RequestParam(value = "noteId", required = false) Long noteId,
                               @RequestParam("content") String content,
                               @RequestParam(value = "summary", required = false) String summary) {
        if (StringUtils.isBlank(userName) || bookId == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        if (StringUtils.isBlank(content)) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "笔记内容不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            Long userId = user.getId();

            boolean deletedOldest = false;
            if (noteId != null && noteId > 0) {
                int updated = noteMapper.updateContent(noteId, userId, bookId, content, summary);
                if (updated <= 0) {
                    return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "笔记不存在或无权限");
                }
                Map<String, Object> resp = new HashMap<>();
                resp.put("noteId", noteId);
                resp.put("deletedOldest", false);
                return CommonResponse.success("已保存", resp);
            }

            Integer cnt = noteMapper.countByUserIdAndBookId(userId, bookId);
            if (cnt != null && cnt >= NOTE_LIMIT) {
                noteMapper.deleteOldestOne(userId, bookId);
                deletedOldest = true;
            }
            Note note = new Note();
            note.setUserId(userId);
            note.setBookId(bookId);
            note.setContent(content);
            note.setSummary(summary);
            noteMapper.insert(note);

            Map<String, Object> resp = new HashMap<>();
            resp.put("noteId", note.getId());
            resp.put("deletedOldest", deletedOldest);
            String msg = deletedOldest ? "已保存（最多 3 条，已自动删除最早的一条）" : "已保存";
            return CommonResponse.success(msg, resp);
        } catch (Exception e) {
            log.error("note save error, userName={}, bookId={}, noteId={}", userName, bookId, noteId, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "保存笔记失败");
        }
    }

    /**
     * 暂存：把当前编辑区内容存为一条新笔记，然后前端清空编辑区开始新笔记。
     * - 超过 3 条时自动删除最早一条，并提示
     */
    @PostMapping("/note/stash")
    public CommonResponse stash(@RequestParam("userName") String userName,
                                @RequestParam("bookId") Long bookId,
                                @RequestParam("content") String content,
                                @RequestParam(value = "summary", required = false) String summary) {
        // stash 本质是“强制新建”
        return save(userName, bookId, null, content, summary);
    }

    /**
     * 导出前生成摘要（<=200字）并写回 note.summary。
     */
    @PostMapping("/note/summarize")
    public CommonResponse summarize(@RequestParam("userName") String userName,
                                    @RequestParam("bookId") Long bookId,
                                    @RequestParam("noteId") Long noteId) {
        if (StringUtils.isBlank(userName) || bookId == null || noteId == null) {
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "参数不能为空");
        }
        try {
            User user = userMapper.getUserByUserName(userName);
            if (user == null || user.getId() == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "用户不存在");
            }
            Note note = noteMapper.selectByIdAndUserIdAndBookId(noteId, user.getId(), bookId);
            if (note == null) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "笔记不存在或无权限");
            }
            String content = note.getContent();
            if (StringUtils.isBlank(content)) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "笔记内容为空");
            }

            String summary = aiReadingService.summarize(content, 200);
            if (summary == null) summary = "";
            summary = summary.trim();
            if (summary.length() > 200) {
                summary = summary.substring(0, 200);
            }

            int updated = noteMapper.updateSummary(noteId, user.getId(), bookId, summary);
            if (updated <= 0) {
                return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "更新摘要失败");
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("noteId", noteId);
            resp.put("summary", summary);
            return CommonResponse.success("摘要已更新", resp);
        } catch (Exception e) {
            log.error("note summarize error, userName={}, bookId={}, noteId={}", userName, bookId, noteId, e);
            return CommonResponse.error(Constants.AI_READING_ERROR_CODE, "生成摘要失败");
        }
    }
}


