package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习附件表
 */
@Data
@TableName("note_file")
public class NoteFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 所属学习记录 */
    private Long learnRecordId;

    /** 原始文件名 */
    private String fileName;

    /** 本地相对路径（含 UUID 文件名） */
    private String filePath;

    /** 类型：pdf/png/jpg */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
