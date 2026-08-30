package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.dto.FeatureLogRequest;
import com.personal.backend.entity.DevelopmentSession;
import com.personal.backend.entity.FeatureLog;
import com.personal.backend.mapper.DevelopmentSessionMapper;
import com.personal.backend.mapper.FeatureLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发会话 + 功能变更 Service
 *
 * 工作流（支持一天多次开始/结束 = 多段会话）：
 * 1. 开始开发：start() 有进行中的会话则幂等返回，否则新建当天一段
 * 2. 开发过程中：addFeature() 记录一条功能变更（挂在进行中的会话下）
 * 3. 结束开发：end() 结束当前进行中的一段，自动计算本段时长
 * 4. summary() 当天统计：多段时长累计 + 功能明细；statistics() 历史汇总
 */
@Service
@RequiredArgsConstructor
public class DevService {

    private final DevelopmentSessionMapper sessionMapper;
    private final FeatureLogMapper featureLogMapper;

    /** 状态：进行中 */
    private static final int STATUS_RUNNING = 0;
    /** 状态：已结束 */
    private static final int STATUS_FINISHED = 1;

    /**
     * 开始开发：已有进行中的会话则幂等返回，否则新建当天一段
     */
    @Transactional
    public DevelopmentSession start() {
        // 已有进行中的会话（跨天未结束也视为同一段）→ 幂等返回
        DevelopmentSession running = getRunningSession();
        if (running != null) {
            // 防御：补齐开始时间
            if (running.getStartTime() == null) {
                running.setStartTime(LocalDateTime.now());
                sessionMapper.updateById(running);
            }
            return running;
        }

        // 无进行中 → 新建当天一段
        DevelopmentSession session = new DevelopmentSession();
        session.setSessionDate(LocalDate.now());
        session.setStartTime(LocalDateTime.now());
        session.setStatus(STATUS_RUNNING);
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 结束开发：结束当前进行中的一段，计算本段时长
     */
    @Transactional
    public DevelopmentSession end() {
        DevelopmentSession session = requireRunningSession();

        session.setEndTime(LocalDateTime.now());
        // 时长按分钟计算，不足 1 分钟按 1 分钟计，避免显示 0
        long minutes = Math.max(1,
                Duration.between(session.getStartTime(), session.getEndTime()).toMinutes());
        session.setDurationMinutes((int) minutes);
        session.setStatus(STATUS_FINISHED);
        sessionMapper.updateById(session);
        return session;
    }

    /**
     * 记录一条功能变更（挂在当前进行中的会话下）
     */
    @Transactional
    public FeatureLog addFeature(FeatureLogRequest request) {
        DevelopmentSession session = requireRunningSession();

        FeatureLog log = new FeatureLog();
        log.setSessionId(session.getId());
        log.setType(request.getType());
        log.setModule(request.getModule());
        log.setContent(request.getContent());
        featureLogMapper.insert(log);
        return log;
    }

    /**
     * 当天统计：多段时长累计 + 各类型功能数量 + 功能明细 + 当天所有会话（前端时段卡用）
     */
    public Map<String, Object> summary(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<DevelopmentSession> sessions = listByDate(target);

        Map<String, Object> result = new HashMap<>();
        result.put("date", target.toString());

        // 累计当天时长：已结束段取 durationMinutes 之和，进行中段按当前时间实时计
        int duration = 0;
        DevelopmentSession running = null;
        for (DevelopmentSession s : sessions) {
            if (s.getStatus() == STATUS_RUNNING) {
                running = s;
                duration += (int) Math.max(1,
                        Duration.between(s.getStartTime(), LocalDateTime.now()).toMinutes());
            } else if (s.getDurationMinutes() != null) {
                duration += s.getDurationMinutes();
            }
        }

        // 当天所有会话下的功能
        List<Long> sessionIds = sessions.stream().map(DevelopmentSession::getId).toList();
        List<FeatureLog> features = sessionIds.isEmpty()
                ? List.of()
                : featureLogMapper.selectList(
                        new LambdaQueryWrapper<FeatureLog>()
                                .in(FeatureLog::getSessionId, sessionIds)
                                .orderByAsc(FeatureLog::getId));

        // 按类型统计：新增 / 修改 / 删除 / 修复
        Map<String, Integer> types = new HashMap<>();
        types.put("新增", 0);
        types.put("修改", 0);
        types.put("删除", 0);
        types.put("修复", 0);
        for (FeatureLog f : features) {
            types.merge(f.getType(), 1, Integer::sum);
        }

        result.put("session", running);
        result.put("sessions", sessions);
        result.put("durationMinutes", duration);
        result.put("featureCount", features.size());
        result.put("types", types);
        result.put("features", features);
        return result;
    }

    /**
     * 历史统计：所有会话 + 总功能数，便于长期回顾
     */
    public Map<String, Object> statistics() {
        List<DevelopmentSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<DevelopmentSession>()
                        .orderByDesc(DevelopmentSession::getSessionDate));

        int totalMinutes = sessions.stream()
                .filter(s -> s.getDurationMinutes() != null)
                .mapToInt(DevelopmentSession::getDurationMinutes)
                .sum();

        long totalFeatures = featureLogMapper.selectCount(null);

        return Map.of(
                "sessionCount", sessions.size(),
                "totalDurationMinutes", totalMinutes,
                "totalFeatures", totalFeatures,
                "sessions", sessions
        );
    }

    /**
     * 范围汇总（开发日志「汇总」Tab）：近 N 天或全部
     * - durationMinutes / sessionCount / featureCount 总量
     * - byModule 按模块功能条数、byType 按类型、byDay 按天开发时长（升序）
     * 进行中的会话不计入历史汇总
     */
    public Map<String, Object> rangeStats(Integer days) {
        LocalDate start = days != null && days > 0 ? LocalDate.now().minusDays(days - 1L) : null;
        List<DevelopmentSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<DevelopmentSession>()
                        .ge(start != null, DevelopmentSession::getSessionDate, start)
                        .orderByAsc(DevelopmentSession::getSessionDate));

        Map<String, Long> byDay = new LinkedHashMap<>();
        long totalMinutes = 0;
        long sessionCount = 0;
        for (DevelopmentSession s : sessions) {
            if (s.getStatus() == STATUS_RUNNING) {
                continue; // 进行中不算历史
            }
            sessionCount++;
            long m = s.getDurationMinutes() != null ? s.getDurationMinutes() : 0;
            totalMinutes += m;
            byDay.merge(s.getSessionDate().toString(), m, Long::sum);
        }

        List<Long> sessionIds = sessions.stream().map(DevelopmentSession::getId).toList();
        List<FeatureLog> features = sessionIds.isEmpty()
                ? List.of()
                : featureLogMapper.selectList(
                        new LambdaQueryWrapper<FeatureLog>()
                                .in(FeatureLog::getSessionId, sessionIds));

        Map<String, Integer> byModule = new LinkedHashMap<>();
        Map<String, Integer> byType = new HashMap<>();
        byType.put("新增", 0);
        byType.put("修改", 0);
        byType.put("删除", 0);
        byType.put("修复", 0);
        for (FeatureLog f : features) {
            byModule.merge(f.getModule(), 1, Integer::sum);
            if (byType.containsKey(f.getType())) {
                byType.merge(f.getType(), 1, Integer::sum);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("durationMinutes", totalMinutes);
        result.put("sessionCount", sessionCount);
        result.put("featureCount", features.size());
        result.put("byDay", byDay);
        result.put("byModule", byModule);
        result.put("byType", byType);
        return result;
    }

    /** 按日期查当天全部会话（一天可多段） */
    private List<DevelopmentSession> listByDate(LocalDate date) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<DevelopmentSession>()
                        .eq(DevelopmentSession::getSessionDate, date)
                        .orderByAsc(DevelopmentSession::getId));
    }

    /** 获取当前进行中的会话，无则 null */
    private DevelopmentSession getRunningSession() {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<DevelopmentSession>()
                        .eq(DevelopmentSession::getStatus, STATUS_RUNNING)
                        .orderByDesc(DevelopmentSession::getStartTime)
                        .last("LIMIT 1"));
    }

    /** 获取当前进行中的会话，没有则抛异常 */
    private DevelopmentSession requireRunningSession() {
        DevelopmentSession session = getRunningSession();
        if (session == null) {
            throw new BizException("当前没有进行中的开发会话，请先调用开始开发");
        }
        return session;
    }
}
