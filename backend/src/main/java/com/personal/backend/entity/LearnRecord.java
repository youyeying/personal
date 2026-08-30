package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习记录表
 */
@Data
@TableName("learn_record")
public class LearnRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 学习主题 1-100位 */
    private String title;

    /** 收获笔记（文本） */
    private String content;

    /** 时长（分钟） */
    private Integer duration;

    /** 方式：阅读/视频/课程/实践/其他 */
    private String way;

    /** 掌握程度 1-5 */
    private Integer mastery;

    /** 学习日期（可补记历史） */
    private LocalDate learnDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
