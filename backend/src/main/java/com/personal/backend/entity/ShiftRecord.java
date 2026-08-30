package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班表记录表：按「每月21日 → 次月20日」一个班期，每天一个班次
 */
@Data
@TableName("shift_record")
public class ShiftRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 日期（班期内每天一条，同日唯一） */
    private LocalDate shiftDate;

    /** 班次名称：早班/中班/晚班/休息等 */
    private String shiftName;

    /** 备注（可选） */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}