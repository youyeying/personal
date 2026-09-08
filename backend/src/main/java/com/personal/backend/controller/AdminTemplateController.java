package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.entity.FoodItem;
import com.personal.backend.service.AdminTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开发端基础数据模板管理接口（仅开发账号 user_type=2 可访问，拦截器校验）
 * - 模板数据 user_id=0，全局共享；业务用户查询 = 模板 + 本人自定义并集
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTemplateController {

    private final AdminTemplateService adminTemplateService;

    // ===================== 食物模板 =====================

    @GetMapping("/foods")
    public Result<List<FoodItem>> listFoods() {
        return Result.ok(adminTemplateService.listFoods());
    }

    @PostMapping("/foods")
    public Result<FoodItem> createFood(@RequestBody FoodItem item) {
        return Result.ok(adminTemplateService.createFood(item), "模板食物已添加");
    }

    @PutMapping("/foods/{id}")
    public Result<FoodItem> updateFood(@PathVariable Long id, @RequestBody FoodItem item) {
        return Result.ok(adminTemplateService.updateFood(id, item), "模板食物已修改");
    }

    @DeleteMapping("/foods/{id}")
    public Result<Void> deleteFood(@PathVariable Long id) {
        adminTemplateService.deleteFood(id);
        return Result.ok(null, "模板食物已删除");
    }

    // ===================== 动作模板 =====================

    @GetMapping("/exercises")
    public Result<List<ExerciseItem>> listExercises() {
        return Result.ok(adminTemplateService.listExercises());
    }

    @PostMapping("/exercises")
    public Result<ExerciseItem> createExercise(@RequestBody ExerciseItem item) {
        return Result.ok(adminTemplateService.createExercise(item), "模板动作已添加");
    }

    @PutMapping("/exercises/{id}")
    public Result<ExerciseItem> updateExercise(@PathVariable Long id, @RequestBody ExerciseItem item) {
        return Result.ok(adminTemplateService.updateExercise(id, item), "模板动作已修改");
    }

    @DeleteMapping("/exercises/{id}")
    public Result<Void> deleteExercise(@PathVariable Long id) {
        adminTemplateService.deleteExercise(id);
        return Result.ok(null, "模板动作已删除");
    }

    // ===================== 分类模板 =====================

    @GetMapping("/categories")
    public Result<List<ExpenseCategory>> listCategories() {
        return Result.ok(adminTemplateService.listCategories());
    }

    @PostMapping("/categories")
    public Result<ExpenseCategory> createCategory(@RequestBody ExpenseCategory category) {
        return Result.ok(adminTemplateService.createCategory(category), "模板分类已添加");
    }

    @PutMapping("/categories/{id}")
    public Result<ExpenseCategory> updateCategory(@PathVariable Long id, @RequestBody ExpenseCategory category) {
        return Result.ok(adminTemplateService.updateCategory(id, category), "模板分类已修改");
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminTemplateService.deleteCategory(id);
        return Result.ok(null, "模板分类已删除");
    }
}
