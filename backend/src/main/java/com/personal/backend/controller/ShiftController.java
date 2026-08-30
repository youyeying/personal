package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.ShiftBatchRequest;
import com.personal.backend.entity.ShiftRecord;
import com.personal.backend.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 班表接口：按「每月21日 → 次月20日」一个班期批量上传、按日期范围查询
 */
@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    /** 批量保存一个班期 */
    @PostMapping("/batch")
    public Result<Map<String, Object>> batch(@Valid @RequestBody ShiftBatchRequest request) {
        return Result.ok(shiftService.batchSave(request), "班表已保存");
    }

    /** 按日期范围查班表 */
    @GetMapping
    public Result<List<ShiftRecord>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(shiftService.list(startDate, endDate));
    }
}