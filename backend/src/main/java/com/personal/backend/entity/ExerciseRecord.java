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
 * 锻炼记录：只存原始参数（重量/个数/分钟/距离/层数/次数/秒/手/备注）
 * - 大卡/MET 消耗由前端按公式计算，不落库（公式迭代历史自动跟随，免重算迁移）
 * - 不同类型动作使用不同字段组合：
 *   strength = weight(可空自重) + reps + minutes + hand
 *   plank    = seconds
 *   walk     = distance + minutes
 *   stairs   = floors + times（时长=层数×次数×12秒/层，前端计算）
 */
@Data
@TableName("exercise_record")
public class ExerciseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 动作（exercise_item 外键） */
    private Long exerciseId;

    /** 锻炼日期（可补记） */
    private LocalDate recordDate;

    /** 重量 kg（力量，自重动作留空） */
    private BigDecimal weight;

    /** 个数（力量/平板） */
    private Integer reps;

    /** 分钟（力量 / 散步，真实时长） */
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