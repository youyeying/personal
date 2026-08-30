package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开发会话表：每天一次会话，记录开发开始/结束时间与时长
 */
@Data
@TableName("development_session")
public class DevelopmentSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 开发日期（同日唯一） */
    private LocalDate sessionDate;

    /** 当天开发开始时间 */
    private LocalDateTime startTime;

    /** 当天开发结束时间 */
    private LocalDateTime endTime;

    /** 开发时长（分钟） */
    private Integer durationMinutes;

    /** 状态：0 进行中 / 1 已结束 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
