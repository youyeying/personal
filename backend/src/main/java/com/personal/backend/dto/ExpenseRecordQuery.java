package com.personal.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 收支记录查询参数
 */
@Data
public class ExpenseRecordQuery {

    /** 1 支出 / 2 收入 */
    @Min(1) @Max(2)
    private Integer type;

    /** 分类 id */
    private Long categoryId;

    /** 开始日期 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 页码 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 20;
}
