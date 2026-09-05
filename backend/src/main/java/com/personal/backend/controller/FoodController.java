package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.FoodMealTemplateRequest;
import com.personal.backend.dto.FoodRecordQuery;
import com.personal.backend.dto.FoodRecordSaveRequest;
import com.personal.backend.entity.FoodItem;
import com.personal.backend.entity.FoodMealTemplate;
import com.personal.backend.entity.FoodRecord;
import com.personal.backend.service.FoodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 饮食接口：食物字典 + 饮食记录 + 整餐模板
 */
@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    // ================= 食物字典 =================

    /** 我的食物列表（收藏优先） */
    @GetMapping("/items")
    public Result<List<FoodItem>> items() {
        return Result.ok(foodService.listItems());
    }

    /** 新增自定义食物 */
    @PostMapping("/items")
    public Result<FoodItem> createItem(@RequestBody FoodItem item) {
        return Result.ok(foodService.createItem(item), "食物已添加");
    }

    /** 修改食物 */
    @PutMapping("/items/{id}")
    public Result<FoodItem> updateItem(@PathVariable Long id, @RequestBody FoodItem item) {
        return Result.ok(foodService.updateItem(id, item), "食物已更新");
    }

    /** 收藏/取消收藏 */
    @PutMapping("/items/{id}/favorite")
    public Result<FoodItem> toggleFavorite(@PathVariable Long id, @RequestParam Boolean favorite) {
        return Result.ok(foodService.toggleFavorite(id, favorite), favorite ? "已收藏" : "已取消收藏");
    }

    /** 删除食物 */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        foodService.deleteItem(id);
        return Result.ok(null, "食物已删除");
    }

    // ================= 饮食记录 =================

    /** 分页查询 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid FoodRecordQuery query) {
        return Result.ok(foodService.page(query));
    }

    /** 区间全量记录（统计/分析页前端聚合） */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(@Valid FoodRecordQuery query) {
        return Result.ok(foodService.statistics(query));
    }

    /** 新增饮食记录 */
    @PostMapping
    public Result<FoodRecord> create(@Valid @RequestBody FoodRecordSaveRequest req) {
        return Result.ok(foodService.create(req), "记录已保存");
    }

    /** 修改饮食记录 */
    @PutMapping("/{id}")
    public Result<FoodRecord> update(@PathVariable Long id, @RequestBody FoodRecordSaveRequest req) {
        return Result.ok(foodService.update(id, req), "记录已更新");
    }

    /** 删除饮食记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        foodService.delete(id);
        return Result.ok(null, "记录已删除");
    }

    // ================= 整餐模板 =================

    /** 我的模板列表 */
    @GetMapping("/templates")
    public Result<List<FoodMealTemplate>> templates() {
        return Result.ok(foodService.listTemplates());
    }

    /** 新增模板 */
    @PostMapping("/templates")
    public Result<FoodMealTemplate> createTemplate(@RequestBody FoodMealTemplateRequest req) {
        return Result.ok(foodService.createTemplate(req), "模板已保存");
    }

    /** 修改模板 */
    @PutMapping("/templates/{id}")
    public Result<FoodMealTemplate> updateTemplate(@PathVariable Long id, @RequestBody FoodMealTemplateRequest req) {
        return Result.ok(foodService.updateTemplate(id, req), "模板已更新");
    }

    /** 删除模板 */
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        foodService.deleteTemplate(id);
        return Result.ok(null, "模板已删除");
    }
}
