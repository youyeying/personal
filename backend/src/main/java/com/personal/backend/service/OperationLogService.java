package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.OperationLogQuery;
import com.personal.backend.entity.OperationLog;
import com.personal.backend.mapper.OperationLogMapper;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 操作日志 Service：记录关键操作 + 查询
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 记录一条操作日志（操作者取当前登录用户）
     *
     * @param module 操作模块
     * @param action 操作动作
     * @param targetId 操作对象 id
     * @param content 操作描述
     */
    public void record(String module, String action, Long targetId, String content) {
        record(UserContext.requireUserId(), module, action, targetId, content);
    }

    /**
     * 记录一条操作日志（操作者显式指定，用于注册等未登录场景）
     */
    public void record(Long userId, String module, String action, Long targetId, String content) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setModule(module);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setContent(content);
        operationLogMapper.insert(log);
    }

    /**
     * 分页查询操作日志（当前登录用户）
     */
    public Map<String, Object> page(OperationLogQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLog::getUserId, userId);
        wrapper.eq(query.getModule() != null && !query.getModule().isBlank(),
                OperationLog::getModule, query.getModule());
        wrapper.eq(query.getAction() != null && !query.getAction().isBlank(),
                OperationLog::getAction, query.getAction());
        // 日期范围按创建时间过滤（结束日期取次日 00:00 前）
        if (query.getStartDate() != null) {
            wrapper.ge(OperationLog::getCreatedAt, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(OperationLog::getCreatedAt, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(OperationLog::getId);

        Page<OperationLog> page = operationLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        return PageUtil.ok(page, page.getRecords());
    }
}
