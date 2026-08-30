package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收支分类接口
 */
@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;

    /** 分类列表（type 可选：1 支出 / 2 收入） */
    @GetMapping
    public Result<List<ExpenseCategory>> list(
            @RequestParam(required = false) @Min(1) @Max(2) Integer type) {
        return Result.ok(categoryService.list(type));
    }

    /** 新增分类 */
    @PostMapping
    public Result<ExpenseCategory> create(@Valid @RequestBody ExpenseCategory category) {
        validateName(category);
        return Result.ok(categoryService.create(category), "新增成功");
    }

    /** 修改分类 */
    @PutMapping("/{id}")
    public Result<ExpenseCategory> update(@PathVariable Long id,
                                          @Valid @RequestBody ExpenseCategory category) {
        validateName(category);
        category.setId(id);
        return Result.ok(categoryService.update(category), "修改成功");
    }

    /** 删除分类 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok(null, "删除成功");
    }

    private void validateName(ExpenseCategory category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new com.personal.backend.common.BizException("分类名不能为空");
        }
        if (category.getName().length() > 20) {
            throw new com.personal.backend.common.BizException("分类名最长 20 字");
        }
    }
}
