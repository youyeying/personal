package com.personal.backend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户信息（由 JWT 解析后放入上下文）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户主键 */
    private Long id;

    /** 登录名 */
    private String username;

    /** 用户类型：1=业务用户 user 表 / 2=开发账号 admin_user 表 */
    private Integer userType;
}
