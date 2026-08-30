package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.LearnRecordQuery;
import com.personal.backend.entity.LearnRecord;
import com.personal.backend.service.LearnRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习记录接口
 */
@RestController
@RequestMapping("/api/learn-records")
@RequiredArgsConstructor
public class LearnRecordController {

    private final LearnRecordService learnRecordService;

    /** 分页查询 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid LearnRecordQuery query) {
        return Result.ok(learnRecordService.page(query));
    }

    /** 学习统计 */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(learnRecordService.statistics());
    }

    /** 新增 */
    @PostMapping
    public Result<LearnRecord> create(@Valid @RequestBody LearnRecord record) {
        return Result.ok(learnRecordService.create(record), "记录成功");
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<LearnRecord> update(@PathVariable Long id,
                                      @Valid @RequestBody LearnRecord record) {
        record.setId(id);
        return Result.ok(learnRecordService.update(record), "修改成功");
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        learnRecordService.delete(id);
        return Result.ok(null, "删除成功");
    }
}
