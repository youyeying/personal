package com.personal.backend.dto;

import lombok.Data;

/**
 * 整餐模板保存请求（新增/修改共用）
 */
@Data
public class FoodMealTemplateRequest {

    /** 模板名（如：工作日早餐） */
    private String name;

    /** JSON：[{foodId, grams, mealType}] */
    private String items;
}
