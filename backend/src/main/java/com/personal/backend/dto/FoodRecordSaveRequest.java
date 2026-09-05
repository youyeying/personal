package com.personal.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饮食记录保存请求（新增/修改共用）
 */
@Data
public class FoodRecordSaveRequest {

    /** 食物 id（必填） */
    private Long foodId;

    /** 饮食日期（可补记） */
    private LocalDate recordDate;

    /** 餐次：breakfast/lunch/dinner/snack */
    private String mealType;

    /** 份量 g */
    private BigDecimal grams;

    /** 备注 */
    private String note;
}
