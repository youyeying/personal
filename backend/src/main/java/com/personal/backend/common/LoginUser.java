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
}
