package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 1-30位 */
    private String username;

    /** 密码哈希（BCrypt） */
    @JsonIgnore
    private String password;

    /** 手机号 11位 */
    private String phone;

    /** 昵称 1-20位 */
    private String nickname;

    /** 头像本地相对路径 */
    private String avatar;

    /** 目标体重 kg */
    private BigDecimal targetWeight;

    /** 年龄（岁，BMR 二期用） */
    private Integer age;

    /** 身高 cm（BMR 二期用） */
    private BigDecimal height;

    /** 性别：male男 / female女（BMR 二期用） */
    private String gender;

    /** 上次修改密码时间（首次为空可免限修改，改后开始一月冷却） */
    private LocalDateTime passwordUpdatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除：0 未删 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
