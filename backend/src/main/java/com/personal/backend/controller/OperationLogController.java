package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.OperationLogQuery;
import com.personal.backend.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 操作日志接口：查询登录用户的操作记录（记录动作由各业务 Service 调用）
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /** 分页查询操作日志 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid OperationLogQuery query) {
        return Result.ok(operationLogService.page(query));
    }
}
