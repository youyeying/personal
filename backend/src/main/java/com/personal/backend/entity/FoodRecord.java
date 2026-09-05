package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 饮食记录：只存食物 + 份量
 * - 营养（热量/蛋白/脂肪/碳水/钠/纤维）由前端按每100g数值 × 份量 ÷ 100 实时计算
 */
@Data
@TableName("food_record")
public class FoodRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 食物（food_item 外键） */
    private Long foodId;

    /** 饮食日期（可补记） */
    private LocalDate recordDate;

    /** 餐次：breakfast/lunch/dinner/snack */
    private String mealType;

    /** 份量 g（默认单位带出可改） */
    private BigDecimal grams;

    /** 备注 */
    private String note;

    /** 创建时间：由 MybatisPlusMetaHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间：插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 软删除：0 未删 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
