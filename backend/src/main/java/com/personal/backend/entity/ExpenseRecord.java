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
 * 收支记录表
 */
@Data
@TableName("expense_record")
public class ExpenseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 1 支出 / 2 收入 */
    private Integer type;

    /** 收支分类 id */
    private Long categoryId;

    /** 金额（两位小数） */
    private BigDecimal amount;

    /** 备注 */
    private String note;

    /** 记账日期（可补记历史） */
    private LocalDate recordDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
