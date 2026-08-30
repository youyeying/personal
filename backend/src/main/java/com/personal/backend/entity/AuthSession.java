package com.personal.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话表：每个「设备」一行，存 refresh token 的哈希
 * - 一个用户可多设备同时登录（手机/电脑各一行）
 * - 刷新时 rotation 原地更新哈希 + 滚动到期时间，旧 token 立即失效
 * - 不存明文，只存 SHA-256 哈希，防库泄露映射回明文
 */
@Data
@TableName("auth_session")
public class AuthSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** refresh token 的 SHA-256 十六进制哈希 */
    private String refreshTokenHash;

    /** refresh 过期时间（每次刷新滚动 +24h） */
    private LocalDateTime expiresAt;

    /** 设备标识（取自浏览器 UA，仅展示用） */
    private String deviceName;

    /** 设备指纹：UA 的 SHA-256（十六进制），登录时按 用户+指纹 复用同一设备会话 */
    private String deviceKey;

    /** 创建时间：由 MybatisPlusMetaHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间：插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 软删除：0 未删 / 1 已删（登出/撤销时置 1） */
    @TableLogic
    private Integer deleted;
}