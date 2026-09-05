package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.*;
import com.personal.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页概览 Service：今日 + 本月三大模块汇总 + 目标进度 + 完成度
 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final WeightRecordMapper weightRecordMapper;
    private final LearnRecordMapper learnRecordMapper;
    private final DailyNoteMapper dailyNoteMapper;
    private final ExpenseCategoryMapper expenseCategoryMapper;
    private final UserMapper userMapper;

    public Map<String, Object> overview() {
        Long userId = UserContext.requireUserId();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        LocalDate monthStart = month.atDay(1);

        Map<String, Object> todayStat = buildTodayStat(userId, today);
        Map<String, Object> monthStat = buildMonthStat(userId, today, monthStart);

        // 全量可支配余额：所有历史收入 − 所有历史支出（发薪入账自动累加，随记账实时变化）
        List<ExpenseRecord> allExpenses = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>().eq(ExpenseRecord::getUserId, userId));
        BigDecimal disposable = sumByType(allExpenses, 2).subtract(sumByType(allExpenses, 1));

        return Map.of("today", todayStat, "month", monthStat, "disposable", disposable);
    }

    /** 今日：收支/笔数、体重、学习、四模块完成度 */
    private Map<String, Object> buildTodayStat(Long userId, LocalDate today) {
        List<ExpenseRecord> todayExpenses = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>()
                        .eq(ExpenseRecord::getUserId, userId)
                        .eq(ExpenseRecord::getRecordDate, today));

        List<LearnRecord> todayLearns = learnRecordMapper.selectList(
                new LambdaQueryWrapper<LearnRecord>()
                        .eq(LearnRecord::getUserId, userId)
                        .eq(LearnRecord::getLearnDate, today));

        boolean noteDone = dailyNoteMapper.selectCount(
                new LambdaQueryWrapper<DailyNote>()
                        .eq(DailyNote::getUserId, userId)
                        .eq(DailyNote::getNoteDate, today)) > 0;

        Map<String, Object> stat = new HashMap<>();
        stat.put("expense", sumByType(todayExpenses, 1));
        stat.put("income", sumByType(todayExpenses, 2));
        stat.put("expenseCount", (int) todayExpenses.stream().filter(r -> r.getType() == 1).count());
        stat.put("incomeCount", (int) todayExpenses.stream().filter(r -> r.getType() == 2).count());

        WeightRecord todayWeight = latestWeight(userId, today);
        stat.put("weight", todayWeight != null ? todayWeight.getWeight() : null);

        stat.put("learnMinutes", todayLearns.stream().mapToInt(r -> duration(r)).sum());
        stat.put("learnCount", todayLearns.size());

        // 今日四模块完成度：记账(收支任一) / 体重 / 学习 / 每日总结
        Map<String, Object> done = new HashMap<>();
        done.put("expense", !todayExpenses.isEmpty());
        done.put("weight", todayWeight != null);
        done.put("learn", !todayLearns.isEmpty());
        done.put("note", noteDone);
        stat.put("done", done);
        return stat;
    }

    /** 本月：收支/结余/学习、体重目标进度、分类构成 */
    private Map<String, Object> buildMonthStat(Long userId, LocalDate today, LocalDate monthStart) {
        List<ExpenseRecord> monthExpenses = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>()
                        .eq(ExpenseRecord::getUserId, userId)
                        .ge(ExpenseRecord::getRecordDate, monthStart)
                        .le(ExpenseRecord::getRecordDate, today));

        BigDecimal monthExpense = sumByType(monthExpenses, 1);
        BigDecimal monthIncome = sumByType(monthExpenses, 2);

        // 最近两次体重（倒序 LIMIT 2，用于较上次变化）
        List<WeightRecord> latestTwo = weightRecordMapper.selectList(
                new LambdaQueryWrapper<WeightRecord>()
                        .eq(WeightRecord::getUserId, userId)
                        .orderByDesc(WeightRecord::getRecordDate)
                        .orderByDesc(WeightRecord::getId)
                        .last("LIMIT 2"));
        // 起始体重（最早一条，日期升序）
        WeightRecord firstWeight = weightRecordMapper.selectOne(
                new LambdaQueryWrapper<WeightRecord>()
                        .eq(WeightRecord::getUserId, userId)
                        .orderByAsc(WeightRecord::getRecordDate)
                        .orderByAsc(WeightRecord::getId)
                        .last("LIMIT 1"));

        User user = userMapper.selectById(userId);

        Map<String, Object> stat = new HashMap<>();
        stat.put("expense", monthExpense);
        stat.put("income", monthIncome);
        stat.put("balance", monthIncome.subtract(monthExpense));
        stat.put("learnMinutes", monthLearnMinutes(userId, monthStart, today));
        stat.put("latestWeight", latestTwo.isEmpty() ? null : latestTwo.get(0).getWeight());
        stat.put("previousWeight", latestTwo.size() > 1 ? latestTwo.get(1).getWeight() : null);
        stat.put("startWeight", firstWeight != null ? firstWeight.getWeight() : null);
        stat.put("targetWeight", user != null ? user.getTargetWeight() : null);
        stat.put("expenseCategories", categoryBreakdown(userId, monthExpenses));
        return stat;
    }

    /** 本月支出分类构成：按金额降序 Top5，其余合并为「其他」 */
    private List<Map<String, Object>> categoryBreakdown(Long userId, List<ExpenseRecord> monthExpenses) {
        // 本月支出金额按分类汇总
        Map<Long, BigDecimal> byCategory = monthExpenses.stream()
                .filter(r -> r.getType() == 1)
                .collect(Collectors.groupingBy(ExpenseRecord::getCategoryId,
                        Collectors.reducing(BigDecimal.ZERO, r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO, BigDecimal::add)));

        if (byCategory.isEmpty()) {
            return List.of();
        }

        // 分类 id → 名称（用户全部分类，含软删过滤由 @TableLogic 自动处理）
        Map<Long, String> names = expenseCategoryMapper.selectList(
                        new LambdaQueryWrapper<ExpenseCategory>().eq(ExpenseCategory::getUserId, userId))
                .stream().collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));

        List<Map.Entry<Long, BigDecimal>> sorted = byCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(sorted.size(), 5); i++) {
            Map.Entry<Long, BigDecimal> e = sorted.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("name", names.getOrDefault(e.getKey(), "未分类"));
            item.put("amount", e.getValue());
            result.add(item);
        }
        // 超出 Top5 的合并为「其他」
        if (sorted.size() > 5) {
            BigDecimal other = sorted.subList(5, sorted.size()).stream()
                    .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> item = new HashMap<>();
            item.put("name", "其他");
            item.put("amount", other);
            result.add(item);
        }
        return result;
    }

    /** 指定日期的体重（同日多条取最新一条） */
    private WeightRecord latestWeight(Long userId, LocalDate date) {
        return weightRecordMapper.selectOne(
                new LambdaQueryWrapper<WeightRecord>()
                        .eq(WeightRecord::getUserId, userId)
                        .eq(WeightRecord::getRecordDate, date)
                        .orderByDesc(WeightRecord::getId)
                        .last("LIMIT 1"));
    }

    private BigDecimal sumByType(List<ExpenseRecord> records, int type) {
        return records.stream()
                .filter(r -> r.getType() == type)
                .map(ExpenseRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int monthLearnMinutes(Long userId, LocalDate start, LocalDate end) {
        return learnRecordMapper.selectList(
                        new LambdaQueryWrapper<LearnRecord>()
                                .eq(LearnRecord::getUserId, userId)
                                .ge(LearnRecord::getLearnDate, start)
                                .le(LearnRecord::getLearnDate, end))
                .stream().mapToInt(this::duration).sum();
    }

    private int duration(LearnRecord r) {
        return r.getDuration() != null ? r.getDuration() : 0;
    }
}