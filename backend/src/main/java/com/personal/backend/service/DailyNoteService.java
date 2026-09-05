package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.*;
import com.personal.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日总结 Service（二期功能，表已建）
 */
@Service
@RequiredArgsConstructor
public class DailyNoteService {

    private final DailyNoteMapper dailyNoteMapper;
    private final OperationLogService operationLogService;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final WeightRecordMapper weightRecordMapper;
    private final LearnRecordMapper learnRecordMapper;
    private final DevelopmentSessionMapper developmentSessionMapper;
    private final ExerciseRecordMapper exerciseRecordMapper;

    /** 某天的总结（无则返回 null） */
    public DailyNote getByDate(LocalDate date) {
        Long userId = UserContext.requireUserId();
        return dailyNoteMapper.selectOne(
                new LambdaQueryWrapper<DailyNote>()
                        .eq(DailyNote::getUserId, userId)
                        .eq(DailyNote::getNoteDate, date)
                        .last("LIMIT 1"));
    }

    /** 保存某天总结（有则更新，无则新增） */
    public DailyNote save(LocalDate date, String mood, String content) {
        Long userId = UserContext.requireUserId();
        DailyNote exist = getByDate(date);

        if (exist == null) {
            DailyNote note = new DailyNote();
            note.setUserId(userId);
            note.setNoteDate(date);
            note.setMood(mood);
            note.setContent(content);
            dailyNoteMapper.insert(note);

            operationLogService.record("NOTE", "CREATE", note.getId(),
                    "写每日总结：" + date);
            return note;
        }

        exist.setMood(mood);
        exist.setContent(content);
        dailyNoteMapper.updateById(exist);

        operationLogService.record("NOTE", "UPDATE", exist.getId(),
                "更新每日总结：" + date);
        return exist;
    }

    /** 按日期范围查总结列表（倒序） */
    public List<DailyNote> list(LocalDate startDate, LocalDate endDate) {
        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<DailyNote> wrapper = new LambdaQueryWrapper<DailyNote>()
                .eq(DailyNote::getUserId, userId);
        if (startDate != null) {
            wrapper.ge(DailyNote::getNoteDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DailyNote::getNoteDate, endDate);
        }
        wrapper.orderByDesc(DailyNote::getNoteDate);
        return dailyNoteMapper.selectList(wrapper);
    }

    /**
     * 某天小汇总：当日 支出/收入、体重（及较上次变化）、学习条数/时长、开发时长
     * 用于每日总结页展示「当天干了什么、减了多少」
     */
    public Map<String, Object> summary(LocalDate date) {
        Long userId = UserContext.requireUserId();

        // 当日收支
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        List<ExpenseRecord> expenses = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>()
                        .eq(ExpenseRecord::getUserId, userId)
                        .eq(ExpenseRecord::getRecordDate, date));
        for (ExpenseRecord r : expenses) {
            if (r.getType() != null && r.getType() == 1) {
                expense = expense.add(r.getAmount());
            } else if (r.getType() != null && r.getType() == 2) {
                income = income.add(r.getAmount());
            }
        }

        // 当日体重 + 较上次变化
        WeightRecord weight = weightRecordMapper.selectOne(
                new LambdaQueryWrapper<WeightRecord>()
                        .eq(WeightRecord::getUserId, userId)
                        .eq(WeightRecord::getRecordDate, date)
                        .orderByDesc(WeightRecord::getId)
                        .last("LIMIT 1"));
        BigDecimal weightValue = weight != null ? weight.getWeight() : null;
        BigDecimal weightChange = null;
        if (weight != null) {
            WeightRecord prev = weightRecordMapper.selectOne(
                    new LambdaQueryWrapper<WeightRecord>()
                            .eq(WeightRecord::getUserId, userId)
                            .lt(WeightRecord::getRecordDate, date)
                            .orderByDesc(WeightRecord::getRecordDate)
                            .orderByDesc(WeightRecord::getId)
                            .last("LIMIT 1"));
            if (prev != null && prev.getWeight() != null) {
                weightChange = weight.getWeight().subtract(prev.getWeight());
            }
        }

        // 当日学习
        List<LearnRecord> learns = learnRecordMapper.selectList(
                new LambdaQueryWrapper<LearnRecord>()
                        .eq(LearnRecord::getUserId, userId)
                        .eq(LearnRecord::getLearnDate, date));
        int learnCount = learns.size();
        int learnMinutes = learns.stream()
                .mapToInt(l -> l.getDuration() != null ? l.getDuration() : 0)
                .sum();

        // 当日开发时长
        int devMinutes = 0;
        DevelopmentSession dev = developmentSessionMapper.selectOne(
                new LambdaQueryWrapper<DevelopmentSession>()
                        .eq(DevelopmentSession::getSessionDate, date)
                        .last("LIMIT 1"));
        if (dev != null) {
            devMinutes = dev.getDurationMinutes() != null
                    ? dev.getDurationMinutes()
                    : (int) Math.max(1, Duration.between(dev.getStartTime(), LocalDateTime.now()).toMinutes());
        }

        // 当日锻炼记录（供前端按 MET 公式算净消耗；收入非每日有，汇总不再强调收入）
        List<ExerciseRecord> exercises = exerciseRecordMapper.selectList(
                new LambdaQueryWrapper<ExerciseRecord>()
                        .eq(ExerciseRecord::getUserId, userId)
                        .eq(ExerciseRecord::getRecordDate, date));

        Map<String, Object> res = new HashMap<>();
        res.put("date", date.toString());
        res.put("expense", expense);
        res.put("income", income);
        res.put("exerciseRecords", exercises);
        res.put("weight", weightValue);
        res.put("weightChange", weightChange);
        res.put("learnCount", learnCount);
        res.put("learnMinutes", learnMinutes);
        res.put("devMinutes", devMinutes);
        return res;
    }
}
