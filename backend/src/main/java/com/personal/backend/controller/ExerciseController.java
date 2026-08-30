package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.ExerciseRecordQuery;
import com.personal.backend.dto.ExerciseRecordSaveRequest;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExerciseRecord;
import com.personal.backend.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 锻炼接口：动作字典 + 锻炼记录
 */
@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    // ================= 动作字典 =================

    /** 我的动作列表 */
    @GetMapping("/items")
    public Result<List<ExerciseItem>> items() {
        return Result.ok(exerciseService.listItems());
    }

    /** 新增自定义动作 */
    @PostMapping("/items")
    public Result<ExerciseItem> createItem(@RequestBody ExerciseItem item) {
        return Result.ok(exerciseService.createItem(item), "动作已添加");
    }

    /** 修改动作 */
    @PutMapping("/items/{id}")
    public Result<ExerciseItem> updateItem(@PathVariable Long id, @RequestBody ExerciseItem item) {
        return Result.ok(exerciseService.updateItem(id, item), "动作已更新");
    }

    /** 删除动作 */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        exerciseService.deleteItem(id);
        return Result.ok(null, "动作已删除");
    }

    // ================= 锻炼记录 =================

    /** 分页查询 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid ExerciseRecordQuery query) {
        return Result.ok(exerciseService.page(query));
    }

    /** 统计（今日/本周/本月 + 近 14 天趋势原始记录，前端算大卡） */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(exerciseService.statistics());
    }

    /** 某动作最近一条记录（打卡页「上次」带出） */
    @GetMapping("/latest")
    public Result<ExerciseRecord> latest(@RequestParam Long exerciseId) {
        return Result.ok(exerciseService.latest(exerciseId));
    }

    /** 新增锻炼记录 */
    @PostMapping
    public Result<ExerciseRecord> create(@Valid @RequestBody ExerciseRecordSaveRequest req) {
        return Result.ok(exerciseService.create(req), "记录已保存");
    }

    /** 修改锻炼记录 */
    @PutMapping("/{id}")
    public Result<ExerciseRecord> update(@PathVariable Long id, @RequestBody ExerciseRecordSaveRequest req) {
        return Result.ok(exerciseService.update(id, req), "记录已更新");
    }

    /** 删除锻炼记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        exerciseService.delete(id);
        return Result.ok(null, "记录已删除");
    }
}