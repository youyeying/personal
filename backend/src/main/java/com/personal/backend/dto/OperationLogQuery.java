package com.personal.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 操作日志查询参数
 */
@Data
public class OperationLogQuery {

    /** 操作模块：EXPENSE/WEIGHT/LEARN/USER/NOTE */
    private String module;

    /** 操作动作：CREATE/UPDATE/DELETE/RESTORE/LOGIN/REGISTER */
    private String action;

    /** 开始日期（按创建时间 >= 当日 00:00） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期（按创建时间 < 次日 00:00） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 20;
}
