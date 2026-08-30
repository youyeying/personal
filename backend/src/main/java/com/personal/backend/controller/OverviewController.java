package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页概览接口
 */
@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    /** 今日 + 本月汇总 */
    @GetMapping
    public Result<Map<String, Object>> overview() {
        return Result.ok(overviewService.overview());
    }
}
