package com.personal.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 锻炼记录保存请求（新增/修改共用）
 * 不同类型动作使用不同字段组合，后端按动作类型校验必填
 */
@Data
public class ExerciseRecordSaveRequest {

    /** 动作 id（必填） */
    private Long exerciseId;

    /** 锻炼日期（可补记） */
    private LocalDate recordDate;

    /** 重量 kg（力量，自重动作留空） */
    private BigDecimal weight;

    /** 个数（力量/平板） */
    private Integer reps;

    /** 分钟（力量 / 散步） */
    private BigDecimal minutes;

    /** 公里（散步） */
    private BigDecimal distance;

    /** 一次爬几层（爬楼梯） */
    private Integer floors;

    /** 爬几次（爬楼梯） */
    private Integer times;

    /** 秒（平板支撑） */
    private Integer seconds;

    /** 左右手：left / right / both */
    private String hand;

    /** 备注 */
    private String note;
}