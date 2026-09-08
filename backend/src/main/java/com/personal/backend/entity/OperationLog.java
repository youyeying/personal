package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表：记录用户关键操作，用于审计追溯
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者 */
    private Long userId;

    /** 操作者类型：1=业务用户 user 表 / 2=开发账号 admin_user 表 */
    private Integer userType;

    /** 操作模块：EXPENSE/WEIGHT/LEARN/USER/NOTE/ADMIN */
    private String module;

    /** 操作动作：CREATE/UPDATE/DELETE/RESTORE/LOGIN/REGISTER */
    private String action;

    /** 操作对象 ID */
    private Long targetId;

    /** 操作内容描述 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 操作人显示名（非表字段，查询时由 Service 填充：业务用户昵称 / 开发账号昵称） */
    @TableField(exist = false)
    private String operatorName;
}
