package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.FeatureLogRequest;
import com.personal.backend.entity.DevelopmentSession;
import com.personal.backend.entity.FeatureLog;
import com.personal.backend.service.DevService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 开发会话 + 功能变更接口
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final DevService devService;

    /** 开始开发：记录当天开始时间 */
    @PostMapping("/session/start")
    public Result<DevelopmentSession> start() {
        return Result.ok(devService.start(), "开始开发");
    }

    /** 结束开发：自动计算开发时长 */
    @PostMapping("/session/end")
    public Result<DevelopmentSession> end() {
        return Result.ok(devService.end(), "结束开发");
    }

    /** 记录一条功能变更 */
    @PostMapping("/features")
    public Result<FeatureLog> addFeature(@Valid @RequestBody FeatureLogRequest request) {
        return Result.ok(devService.addFeature(request), "功能变更已记录");
    }

    /** 当天统计：开发时长 + 新增/修改/删除/修复明细（date 缺省为今天） */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(devService.summary(date));
    }

    /** 历史统计：会话列表 + 总时长 + 总功能数 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(devService.statistics());
    }

    /** 范围汇总（开发日志「汇总」Tab）：近 N 天或全部，按模块/类型/按天聚合 */
    @GetMapping("/statistics/range")
    public Result<Map<String, Object>> rangeStats(
            @RequestParam(required = false) Integer days) {
        return Result.ok(devService.rangeStats(days));
    }
}
