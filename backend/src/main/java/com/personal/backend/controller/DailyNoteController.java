package com.personal.backend.controller;

import com.personal.backend.common.BizException;
import com.personal.backend.common.Result;
import com.personal.backend.entity.DailyNote;
import com.personal.backend.service.DailyNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日总结接口
 */
@RestController
@RequestMapping("/api/daily-notes")
@RequiredArgsConstructor
public class DailyNoteController {

    private final DailyNoteService dailyNoteService;

    /** 某天总结 */
    @GetMapping("/date")
    public Result<DailyNote> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dailyNoteService.getByDate(date));
    }

    /** 保存总结（mood 心情 / content 内容，二者至少填一项） */
    @PostMapping
    public Result<DailyNote> save(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) Map<String, String> body) {
        String mood = body != null ? body.get("mood") : null;
        String content = body != null ? body.get("content") : null;
        if ((mood == null || mood.isBlank()) && (content == null || content.isBlank())) {
            throw new BizException("心情或内容至少填写一项");
        }
        return Result.ok(dailyNoteService.save(date, mood, content), "保存成功");
    }

    /** 按日期范围查总结列表 */
    @GetMapping
    public Result<List<DailyNote>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(dailyNoteService.list(startDate, endDate));
    }

    /** 某天小汇总：支出/收入、体重变化、学习、开发 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dailyNoteService.summary(date));
    }
}
