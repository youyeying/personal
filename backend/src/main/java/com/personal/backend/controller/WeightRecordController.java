package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.dto.WeightRecordQuery;
import com.personal.backend.entity.WeightRecord;
import com.personal.backend.service.WeightRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 体重记录接口
 */
@RestController
@RequestMapping("/api/weight-records")
@RequiredArgsConstructor
public class WeightRecordController {

    private final WeightRecordService weightRecordService;

    /** 分页查询 */
    @GetMapping
    public Result<Map<String, Object>> page(@Valid WeightRecordQuery query) {
        return Result.ok(weightRecordService.page(query));
    }

    /** 体重趋势（日期升序） */
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend() {
        return Result.ok(weightRecordService.trend());
    }

    /** 打卡 */
    @PostMapping
    public Result<WeightRecord> create(@Valid @RequestBody WeightRecord record) {
        return Result.ok(weightRecordService.create(record), "打卡成功");
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<WeightRecord> update(@PathVariable Long id,
                                       @Valid @RequestBody WeightRecord record) {
        record.setId(id);
        return Result.ok(weightRecordService.update(record), "修改成功");
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        weightRecordService.delete(id);
        return Result.ok(null, "删除成功");
    }
}
