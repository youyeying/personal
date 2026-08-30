package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日总结表（二期启用）
 */
@Data
@TableName("daily_note")
public class DailyNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 日期（同日唯一） */
    private LocalDate noteDate;

    /** 心情 */
    private String mood;

    /** 今日小结 */
    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
