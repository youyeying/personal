package com.personal.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 班表批量保存请求：一次上传一个班期（如 每月21日 → 次月20日）
 */
@Data
public class ShiftBatchRequest {

    /** 班期开始日期 */
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 班期结束日期 */
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 班期明细（每天一条） */
    @NotEmpty
    private List<ShiftItem> shifts;

    @Data
    public static class ShiftItem {
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;

        @NotEmpty
        private String shiftName;

        private String note;
    }
}