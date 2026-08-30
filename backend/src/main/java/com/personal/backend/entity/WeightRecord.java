package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 体重记录表
 */
@Data
@TableName("weight_record")
public class WeightRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 体重 kg */
    private BigDecimal weight;

    /** 体脂率（可选） */
    private BigDecimal bodyFat;

    /** 腰围 cm（可选） */
    private BigDecimal waist;

    /** 备注 */
    private String note;

    /** 记录日期（可补记历史） */
    private LocalDate recordDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
