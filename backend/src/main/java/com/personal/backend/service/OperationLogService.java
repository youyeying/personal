package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.OperationLogQuery;
import com.personal.backend.entity.AdminUser;
import com.personal.backend.entity.OperationLog;
import com.personal.backend.entity.User;
import com.personal.backend.mapper.AdminUserMapper;
import com.personal.backend.mapper.OperationLogMapper;
import com.personal.backend.mapper.UserMapper;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志 Service：记录关键操作 + 查询
 * - 业务用户：只查自己的日志
 * - 开发账号：查全部日志（审计），并展示操作人姓名（区分业务用户/开发账号）
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;
    private final AdminUserMapper adminUserMapper;

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
     * 记录一条操作日志（操作者显式指定 userId；操作者类型取当前上下文，未登录按业务用户）
     * 用于注册等传入明确 userId 的场景（AuthService 登录/注册记录）
     */
    public void record(Long userId, String module, String action, Long targetId, String content) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUserType(UserContext.get() != null && UserContext.get().getUserType() != null
                ? UserContext.get().getUserType() : 1);
        log.setModule(module);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setContent(content);
        operationLogMapper.insert(log);
    }

    /**
     * 分页查询操作日志：
     * - 业务用户：仅自己的；开发账号：全部（审计）
     * - 返回记录附 operatorName（操作人显示名，区分 user / admin_user）
     */
    public Map<String, Object> page(OperationLogQuery query) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        // 业务用户只看自己的；开发账号（userType=2）全量可见
        if (!UserContext.isAdmin()) {
            wrapper.eq(OperationLog::getUserId, UserContext.requireUserId());
        }
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

        // 批量翻译操作人姓名（user 表 + admin_user 表，按 user_type 分组一次查出）
        List<OperationLog> records = page.getRecords();
        fillOperatorNames(records);

        return PageUtil.ok(page, records);
    }

    /** 为日志记录填充 operatorName（操作人显示名） */
    private void fillOperatorNames(List<OperationLog> records) {
        // 收集 user_type=1 的 userId（业务用户）与 user_type=2 的 userId（开发账号）
        Set<Long> userIds = new HashSet<>();
        Set<Long> adminIds = new HashSet<>();
        for (OperationLog log : records) {
            if (log.getUserType() != null && log.getUserType() == 2) {
                adminIds.add(log.getUserId());
            } else {
                userIds.add(log.getUserId());
            }
        }
        Map<Long, String> userNames = userIds.isEmpty() ? Map.of()
                : queryNames(userMapper, userIds);
        Map<Long, String> adminNames = adminIds.isEmpty() ? Map.of()
                : queryNames(adminUserMapper, adminIds);

        for (OperationLog log : records) {
            boolean isAdmin = log.getUserType() != null && log.getUserType() == 2;
            String name = isAdmin ? adminNames.get(log.getUserId()) : userNames.get(log.getUserId());
            log.setOperatorName(name == null ? (isAdmin ? "开发账号" : "用户") : name);
        }
    }

    /** 批量按 id 查名称（nickname 优先） */
    private Map<Long, String> queryNames(com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper, Set<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        try {
            List<?> list;
            if (mapper instanceof UserMapper um) {
                list = um.selectBatchIds(ids);
                for (Object o : list) {
                    User u = (User) o;
                    names.put(u.getId(), u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername());
                }
            } else {
                List<AdminUser> admins = ((AdminUserMapper) mapper).selectBatchIds(ids);
                for (AdminUser a : admins) {
                    names.put(a.getId(), a.getNickname() != null && !a.getNickname().isBlank() ? a.getNickname() : a.getUsername());
                }
            }
        } catch (Exception ignored) {
            // 名称翻译失败不影响日志列表
        }
        return names;
    }
}
