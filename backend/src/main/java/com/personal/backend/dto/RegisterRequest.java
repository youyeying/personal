package com.personal.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {

    /** 登录名 1-30位 */
    @NotBlank(message = "登录名不能为空")
    @Size(min = 1, max = 30, message = "登录名长度需为 1-30 位")
    private String username;

    /** 明文密码 8-16位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 16, message = "密码长度需为 8-16 位")
    private String password;

    /** 手机号 11位 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号需为 11 位数字")
    private String phone;

    /** 昵称 1-20位 */
    @Size(min = 1, max = 20, message = "昵称长度需为 1-20 位")
    private String nickname;
}
