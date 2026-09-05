package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食物字典：每 100g 营养数值 + 默认份量参考
 * - 摄入营养由前端按份量实时折算（大卡不落库，与锻炼 MET 同模式）
 * - favorite=1 收藏（记录页「常用」分组优先展示）
 * - 新用户注册由 AuthService 复制 user_id=1 的默认食物
 */
@Data
@TableName("food_item")
public class FoodItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 食物名 */
    private String name;

    /** 类型：staple主食/protein肉蛋/veg蔬菜/fruit水果/snack零食饮品/other */
    private String type;

    /** 每100g热量 kcal */
    private BigDecimal kcal;

    /** 每100g蛋白质 g */
    private BigDecimal protein;

    /** 每100g脂肪 g */
    private BigDecimal fat;

    /** 每100g碳水 g */
    private BigDecimal carbs;

    /** 每100g钠 mg */
    private Integer sodium;

    /** 每100g膳食纤维 g */
    private BigDecimal fiber;

    /** 默认份量 g（个/根/碗/盒等参考值，成熟App口径） */
    private BigDecimal defaultGrams;

    /** 默认单位标签：个/根/碗/盒/ml/块/杯/份/片/把/袋/罐 */
    private String unitLabel;

    /** 收藏 0否/1是 */
    private Boolean favorite;

    /** 排序（同组内） */
    private Integer sortOrder;

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
