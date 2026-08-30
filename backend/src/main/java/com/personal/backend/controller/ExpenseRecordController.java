package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.ExpenseRecordQuery;
import com.personal.backend.entity.ExpenseRecord;
import com.personal.backend.service.ExpenseRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 收支记录接口
 */
@RestController
@RequestMapping("/api/expense-records")
@RequiredArgsConstructor
public class ExpenseRecordController {

    private final ExpenseRecordService recordService;

    /** 分页查询 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid ExpenseRecordQuery query) {
        return Result.ok(recordService.page(query));
    }

    /** 统计：总支出 / 总收入 / 分类汇总 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(@Valid ExpenseRecordQuery query) {
        return Result.ok(recordService.statistics(query));
    }

    /** 新增 */
    @PostMapping
    public Result<ExpenseRecord> create(@Valid @RequestBody ExpenseRecord record) {
        return Result.ok(recordService.create(record), "记账成功");
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<ExpenseRecord> update(@PathVariable Long id,
                                        @Valid @RequestBody ExpenseRecord record) {
        record.setId(id);
        return Result.ok(recordService.update(record), "修改成功");
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return Result.ok(null, "删除成功");
    }
}
