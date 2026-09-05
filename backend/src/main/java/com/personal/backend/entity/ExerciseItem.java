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
 * 锻炼动作字典：每个动作的类型 / 基础 MET / 参考速度（强度系数基准）
 * - type: strength(个数+分钟) / plank(秒) / walk(距离+分钟) / stairs(层数+次数)
 * - base_met=0 表示由规则动态计算（如散步按速度定档）
 * - 新用户注册由 AuthService 复制 user_id=1 的默认动作
 */
@Data
@TableName("exercise_item")
public class ExerciseItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 动作名（保留用户叫法） */
    private String name;

    /** 类型：strength/plank/walk/stairs */
    private String type;

    /** 基础 MET（0=动态计算） */
    private BigDecimal baseMet;

    /** 参考速度（个/分钟，用户平均节奏=中等强度基准；非计数类为空） */
    private Integer refSpeed;

    /** 速度上限（个/分钟，世界纪录封顶防 MET 爆炸；缺省参考速度×3） */
    private Integer maxSpeed;

    /** 是否记重量 */
    private Boolean hasWeight;

    /** 是否记左右手 */
    private Boolean hasHand;

    /** 排序 */
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