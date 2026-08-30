package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 功能变更记录表：开发过程中记录新增/修改/删除的功能
 */
@Data
@TableName("feature_log")
public class FeatureLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属开发会话 */
    private Long sessionId;

    /** 变更类型：新增/修改/删除/修复 */
    private String type;

    /** 所属模块：记账/健康/学习/系统/其他 */
    private String module;

    /** 功能变更描述 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
