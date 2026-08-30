package com.personal.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 */
@Data
public class ChangePasswordRequest {

    /** 原密码 */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码 8-16位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 16, message = "新密码长度需为 8-16 位")
    private String newPassword;
}
