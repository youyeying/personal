package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开发账号表（管理员）：与业务用户 user 表物理隔离
 * 仅开发人员使用，访问开发日志 / 操作日志 / 基础数据模板管理（auth_session.user_type=2）
 */
@Data
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 1-30位 */
    private String username;

    /** 密码哈希（BCrypt，8-16 位且含数字+大写+小写字母） */
    @JsonIgnore
    private String password;

    /** 手机号 11位 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像本地相对路径 */
    private String avatar;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除：0 未删 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
