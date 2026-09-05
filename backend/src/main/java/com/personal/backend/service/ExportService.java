package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.*;
import com.personal.backend.mapper.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据导出：全模块记录 → CSV（单模块）/ JSON（全量打包）
 * - CSV 带 UTF-8 BOM（Excel 中文兼容）；含逗号/引号/换行的值按 RFC 4180 转义
 * - JSON 一次导出全部模块 + 字典 + 元信息（导出时间），用于备份迁移
 */
@Service
public class ExportService {

    private final ExpenseRecordMapper expenseMapper;
    private final ExpenseCategoryMapper expenseCategoryMapper;
    private final ExerciseRecordMapper exerciseMapper;
    private final ExerciseItemMapper exerciseItemMapper;
    private final FoodRecordMapper foodMapper;
    private final FoodItemMapper foodItemMapper;
    private final WeightRecordMapper weightMapper;
    private final LearnRecordMapper learnMapper;
    private final DailyNoteMapper dailyNoteMapper;

    public ExportService(ExpenseRecordMapper expenseMapper, ExpenseCategoryMapper expenseCategoryMapper,
                          ExerciseRecordMapper exerciseMapper, ExerciseItemMapper exerciseItemMapper,
                          FoodRecordMapper foodMapper, FoodItemMapper foodItemMapper,
                          WeightRecordMapper weightMapper, LearnRecordMapper learnMapper,
                          DailyNoteMapper dailyNoteMapper) {
        this.expenseMapper = expenseMapper;
        this.expenseCategoryMapper = expenseCategoryMapper;
        this.exerciseMapper = exerciseMapper;
        this.exerciseItemMapper = exerciseItemMapper;
        this.foodMapper = foodMapper;
        this.foodItemMapper = foodItemMapper;
        this.weightMapper = weightMapper;
        this.learnMapper = learnMapper;
        this.dailyNoteMapper = dailyNoteMapper;
    }

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /* ---------- CSV（表头 + 数据行） ---------- */

    /** 记账：日期/类型/分类/金额/备注 */
    public List<String[]> expenseCsv() {
        Long uid = UserContext.requireUserId();
        Map<Long, String> catNames = expenseCategoryMapper.selectList(
                        new LambdaQueryWrapper<ExpenseCategory>().eq(ExpenseCategory::getUserId, uid))
                .stream().collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "类型", "分类", "金额", "备注", "记录时间"});
        for (ExpenseRecord r : expenseMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>().eq(ExpenseRecord::getUserId, uid).orderByAsc(ExpenseRecord::getRecordDate))) {
            rows.add(new String[]{
                    String.valueOf(r.getRecordDate()),
                    r.getType() != null && r.getType() == 1 ? "收入" : "支出",
                    catNames.getOrDefault(r.getCategoryId(), String.valueOf(r.getCategoryId())),
                    String.valueOf(r.getAmount()),
                    nz(r.getNote()),
                    r.getCreatedAt() == null ? "" : r.getCreatedAt().format(DT)});
        }
        return rows;
    }

    /** 锻炼：日期/动作/重量/个数/分钟/公里/层数/次数/秒/手/体重快照/备注 */
    public List<String[]> exerciseCsv() {
        Long uid = UserContext.requireUserId();
        Map<Long, String> itemNames = exerciseItemMapper.selectList(
                        new LambdaQueryWrapper<ExerciseItem>().eq(ExerciseItem::getUserId, uid))
                .stream().collect(Collectors.toMap(ExerciseItem::getId, ExerciseItem::getName));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "动作", "重量kg", "个数", "分钟", "公里", "层数", "次数", "秒", "手", "体重快照kg", "备注"});
        for (ExerciseRecord r : exerciseMapper.selectList(
                new LambdaQueryWrapper<ExerciseRecord>().eq(ExerciseRecord::getUserId, uid).orderByAsc(ExerciseRecord::getRecordDate))) {
            rows.add(new String[]{
                    String.valueOf(r.getRecordDate()),
                    itemNames.getOrDefault(r.getExerciseId(), String.valueOf(r.getExerciseId())),
                    nb(r.getWeight()), ni(r.getReps()), nb(r.getMinutes()), nb(r.getDistance()),
                    ni(r.getFloors()), ni(r.getTimes()), ni(r.getSeconds()), nz(r.getHand()),
                    nb(r.getBodyWeight()), nz(r.getNote())});
        }
        return rows;
    }

    /** 饮食：日期/餐次/食物/份量g/备注（每100g营养见 JSON 导出或食物编辑弹窗） */
    public List<String[]> foodCsv() {
        Long uid = UserContext.requireUserId();
        Map<Long, String> foodNames = foodItemMapper.selectList(
                        new LambdaQueryWrapper<FoodItem>().eq(FoodItem::getUserId, uid))
                .stream().collect(Collectors.toMap(FoodItem::getId, FoodItem::getName));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "餐次", "食物", "份量g/ml", "每100g热量", "备注"});
        Map<Long, FoodItem> foods = foodItemMapper.selectList(
                        new LambdaQueryWrapper<FoodItem>().eq(FoodItem::getUserId, uid))
                .stream().collect(Collectors.toMap(FoodItem::getId, f -> f));
        for (FoodRecord r : foodMapper.selectList(
                new LambdaQueryWrapper<FoodRecord>().eq(FoodRecord::getUserId, uid).orderByAsc(FoodRecord::getRecordDate))) {
            FoodItem f = foods.get(r.getFoodId());
            rows.add(new String[]{
                    String.valueOf(r.getRecordDate()),
                    mealLabel(r.getMealType()),
                    foodNames.getOrDefault(r.getFoodId(), String.valueOf(r.getFoodId())),
                    nb(r.getGrams()),
                    f == null ? "" : String.valueOf(f.getKcal()),
                    nz(r.getNote())});
        }
        return rows;
    }

    /** 体重：日期/体重/体脂率/腰围/备注 */
    public List<String[]> weightCsv() {
        Long uid = UserContext.requireUserId();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "体重kg", "体脂率%", "腰围cm", "备注"});
        for (WeightRecord r : weightMapper.selectList(
                new LambdaQueryWrapper<WeightRecord>().eq(WeightRecord::getUserId, uid).orderByAsc(WeightRecord::getRecordDate))) {
            rows.add(new String[]{
                    String.valueOf(r.getRecordDate()), nb(r.getWeight()), nb(r.getBodyFat()), nb(r.getWaist()), nz(r.getNote())});
        }
        return rows;
    }

    /** 学习：日期/主题/方式/时长min/掌握/内容 */
    public List<String[]> learnCsv() {
        Long uid = UserContext.requireUserId();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "主题", "方式", "时长min", "掌握", "内容"});
        for (LearnRecord r : learnMapper.selectList(
                new LambdaQueryWrapper<LearnRecord>().eq(LearnRecord::getUserId, uid).orderByAsc(LearnRecord::getLearnDate))) {
            rows.add(new String[]{
                    String.valueOf(r.getLearnDate()), nz(r.getTitle()), nz(r.getWay()),
                    ni(r.getDuration()), ni(r.getMastery()), nz(r.getContent())});
        }
        return rows;
    }

    /** 每日总结：日期/心情/小结 */
    public List<String[]> dailyNoteCsv() {
        Long uid = UserContext.requireUserId();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"日期", "心情", "小结"});
        for (DailyNote r : dailyNoteMapper.selectList(
                new LambdaQueryWrapper<DailyNote>().eq(DailyNote::getUserId, uid).orderByAsc(DailyNote::getNoteDate))) {
            rows.add(new String[]{String.valueOf(r.getNoteDate()), nz(r.getMood()), nz(r.getContent())});
        }
        return rows;
    }

    /** 按模块名取 CSV（未知模块抛业务异常） */
    public List<String[]> csvOf(String module) {
        switch (module) {
            case "expense": return expenseCsv();
            case "exercise": return exerciseCsv();
            case "food": return foodCsv();
            case "weight": return weightCsv();
            case "learn": return learnCsv();
            case "dailyNote": return dailyNoteCsv();
            default: throw new com.personal.backend.common.BizException("未知导出模块：" + module);
        }
    }

    /* ---------- JSON（全量打包备份） ---------- */

    public Map<String, Object> exportAll() {
        Long uid = UserContext.requireUserId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", LocalDateTime.now().format(DT));
        data.put("expenseRecords", expenseMapper.selectList(new LambdaQueryWrapper<ExpenseRecord>().eq(ExpenseRecord::getUserId, uid)));
        data.put("expenseCategories", expenseCategoryMapper.selectList(new LambdaQueryWrapper<ExpenseCategory>().eq(ExpenseCategory::getUserId, uid)));
        data.put("exerciseRecords", exerciseMapper.selectList(new LambdaQueryWrapper<ExerciseRecord>().eq(ExerciseRecord::getUserId, uid)));
        data.put("exerciseItems", exerciseItemMapper.selectList(new LambdaQueryWrapper<ExerciseItem>().eq(ExerciseItem::getUserId, uid)));
        data.put("foodRecords", foodMapper.selectList(new LambdaQueryWrapper<FoodRecord>().eq(FoodRecord::getUserId, uid)));
        data.put("foodItems", foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>().eq(FoodItem::getUserId, uid)));
        data.put("weightRecords", weightMapper.selectList(new LambdaQueryWrapper<WeightRecord>().eq(WeightRecord::getUserId, uid)));
        data.put("learnRecords", learnMapper.selectList(new LambdaQueryWrapper<LearnRecord>().eq(LearnRecord::getUserId, uid)));
        data.put("dailyNotes", dailyNoteMapper.selectList(new LambdaQueryWrapper<DailyNote>().eq(DailyNote::getUserId, uid)));
        return data;
    }

    /* ---------- 工具 ---------- */

    private static String mealLabel(String meal) {
        switch (meal == null ? "" : meal) {
            case "breakfast": return "早餐";
            case "lunch": return "午餐";
            case "dinner": return "晚餐";
            case "snack": return "加餐";
            default: return nz(meal);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String nb(java.math.BigDecimal v) { return v == null ? "" : String.valueOf(v); }
    private static String ni(Integer v) { return v == null ? "" : String.valueOf(v); }
}
