package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.LearnRecordQuery;
import com.personal.backend.entity.LearnRecord;
import com.personal.backend.entity.NoteFile;
import com.personal.backend.mapper.LearnRecordMapper;
import com.personal.backend.mapper.NoteFileMapper;
import com.personal.backend.utils.OwnedUtil;
import com.personal.backend.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习记录 Service：增删改查 + 附件 + 统计
 */
@Service
@RequiredArgsConstructor
public class LearnRecordService {

    /** 学习方式白名单 */
    private static final Set<String> WAYS = Set.of("阅读", "视频", "课程", "实践", "其他");

    private final LearnRecordMapper learnRecordMapper;
    private final NoteFileMapper noteFileMapper;
    private final OperationLogService operationLogService;

    /** 分页查询（附带附件列表） */
    public Map<String, Object> page(LearnRecordQuery query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<LearnRecord> wrapper = new LambdaQueryWrapper<LearnRecord>()
                .eq(LearnRecord::getUserId, userId);
        if (query.getWay() != null && !query.getWay().isBlank()) {
            wrapper.eq(LearnRecord::getWay, query.getWay());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w
                    .like(LearnRecord::getTitle, query.getKeyword())
                    .or().like(LearnRecord::getContent, query.getKeyword()));
        }
        if (query.getStartDate() != null) {
            wrapper.ge(LearnRecord::getLearnDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(LearnRecord::getLearnDate, query.getEndDate());
        }
        wrapper.orderByDesc(LearnRecord::getLearnDate).orderByDesc(LearnRecord::getId);

        Page<LearnRecord> page = learnRecordMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        // 批量取记录 id 对应附件，避免 N+1
        List<Long> recordIds = page.getRecords().stream().map(LearnRecord::getId).toList();
        Map<Long, List<NoteFile>> filesByRecord = loadFilesByRecordIds(recordIds);

        List<Map<String, Object>> records = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("content", r.getContent());
            m.put("duration", r.getDuration());
            m.put("way", r.getWay());
            m.put("mastery", r.getMastery());
            m.put("learnDate", r.getLearnDate());
            m.put("createdAt", r.getCreatedAt());
            m.put("files", filesByRecord.getOrDefault(r.getId(), List.of()));
            return m;
        }).collect(Collectors.toList());

        return PageUtil.ok(page, records);
    }

    /** 新增学习记录 */
    @Transactional
    public LearnRecord create(LearnRecord record) {
        Long userId = UserContext.requireUserId();
        validate(record);

        record.setId(null);
        record.setUserId(userId);
        learnRecordMapper.insert(record);

        operationLogService.record("LEARN", "CREATE", record.getId(),
                "新增学习记录：" + record.getTitle());
        return record;
    }

    /** 修改学习记录 */
    @Transactional
    public LearnRecord update(LearnRecord record) {
        Long userId = UserContext.requireUserId();
        OwnedUtil.requireOwned(learnRecordMapper, record.getId(), userId,
                LearnRecord::getUserId, "学习记录不存在");
        validate(record);

        record.setUserId(userId);
        learnRecordMapper.updateById(record);

        operationLogService.record("LEARN", "UPDATE", record.getId(),
                "修改学习记录：" + record.getTitle());
        return record;
    }

    /** 删除学习记录（级联软删除附件） */
    @Transactional
    public void delete(Long id) {
        Long userId = UserContext.requireUserId();
        LearnRecord exist = OwnedUtil.requireOwned(learnRecordMapper, id, userId,
                LearnRecord::getUserId, "学习记录不存在");

        // 级联软删除附件记录（物理文件由 FileController 单独清理）
        List<NoteFile> files = noteFileMapper.selectList(
                new LambdaQueryWrapper<NoteFile>().eq(NoteFile::getLearnRecordId, id));
        for (NoteFile f : files) {
            noteFileMapper.deleteById(f.getId());
        }

        learnRecordMapper.deleteById(id);

        operationLogService.record("LEARN", "DELETE", id,
                "删除学习记录：" + exist.getTitle());
    }

    /** 学习统计：今日/本月/累计时长 + 掌握均分 + 方式分布 + 近14天趋势 + 掌握分布 */
    public Map<String, Object> statistics() {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();
        List<LearnRecord> all = learnRecordMapper.selectList(
                new LambdaQueryWrapper<LearnRecord>().eq(LearnRecord::getUserId, userId));

        int totalDuration = all.stream()
                .filter(r -> r.getDuration() != null)
                .mapToInt(LearnRecord::getDuration)
                .sum();

        // 今日 / 本月学习时长
        YearMonth month = YearMonth.from(today);
        LocalDate monthStart = month.atDay(1);
        int todayMinutes = all.stream()
                .filter(r -> today.equals(r.getLearnDate()))
                .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0).sum();
        int monthMinutes = all.stream()
                .filter(r -> r.getLearnDate() != null
                        && !r.getLearnDate().isBefore(monthStart)
                        && !r.getLearnDate().isAfter(today))
                .mapToInt(r -> r.getDuration() != null ? r.getDuration() : 0).sum();

        // 掌握程度均分（无记录时为 null）
        List<Integer> masteries = all.stream()
                .map(LearnRecord::getMastery)
                .filter(Objects::nonNull)
                .toList();
        Double avgMastery = masteries.isEmpty() ? null
                : Math.round(masteries.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0) / 10.0;

        // 按方式计数
        Map<String, Integer> byWay = new HashMap<>();
        for (LearnRecord r : all) {
            byWay.merge(r.getWay(), 1, Integer::sum);
        }

        // 掌握程度分布（1-5）
        Map<Integer, Integer> mastery = new HashMap<>();
        for (Integer m : masteries) {
            mastery.merge(m, 1, Integer::sum);
        }

        // 近 14 天（含今天）学习时长趋势，逐日合计
        LocalDate start = today.minusDays(13);
        Map<LocalDate, Integer> dayMinutes = new HashMap<>();
        for (LearnRecord r : all) {
            LocalDate d = r.getLearnDate();
            if (d != null && !d.isBefore(start) && !d.isAfter(today)) {
                dayMinutes.merge(d, r.getDuration() != null ? r.getDuration() : 0, Integer::sum);
            }
        }
        List<String> trendDates = new ArrayList<>();
        List<Integer> trendMinutes = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            trendDates.add(d.toString());
            trendMinutes.add(dayMinutes.getOrDefault(d, 0));
        }
        Map<String, Object> trend = Map.of("dates", trendDates, "minutes", trendMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", all.size());
        result.put("totalDuration", totalDuration);
        result.put("todayMinutes", todayMinutes);
        result.put("monthMinutes", monthMinutes);
        result.put("avgMastery", avgMastery);
        result.put("byWay", byWay);
        result.put("mastery", mastery);
        result.put("trend", trend);
        return result;
    }

    /** 校验：主题必填、方式白名单、掌握程度 1-5 */
    private void validate(LearnRecord record) {
        if (record.getTitle() == null || record.getTitle().isBlank()) {
            throw new BizException("学习主题不能为空");
        }
        if (record.getTitle().length() > 100) {
            throw new BizException("学习主题最长 100 字");
        }
        if (record.getLearnDate() == null) {
            throw new BizException("学习日期不能为空");
        }
        if (!WAYS.contains(record.getWay())) {
            throw new BizException("学习方式仅支持：阅读/视频/课程/实践/其他");
        }
        if (record.getMastery() != null
                && (record.getMastery() < 1 || record.getMastery() > 5)) {
            throw new BizException("掌握程度需为 1-5");
        }
    }

    /** 批量加载记录附件 */
    private Map<Long, List<NoteFile>> loadFilesByRecordIds(List<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        List<NoteFile> files = noteFileMapper.selectList(
                new LambdaQueryWrapper<NoteFile>().in(NoteFile::getLearnRecordId, recordIds));
        return files.stream().collect(Collectors.groupingBy(NoteFile::getLearnRecordId));
    }
}
