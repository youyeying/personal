package com.personal.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改个人信息请求
 */
@Data
public class UpdateProfileRequest {

    /** 昵称 1-20位 */
    @Size(min = 1, max = 20, message = "昵称长度需为 1-20 位")
    private String nickname;

    /** 手机号 11位 */
    @Pattern(regexp = "^1\\d{10}$", message = "手机号需为 11 位数字")
    private String phone;

    /** 头像相对路径 */
    private String avatar;

    /** 目标体重 kg */
    private BigDecimal targetWeight;

    /** 清除目标体重（true 时忽略 targetWeight，置空） */
    private Boolean clearTargetWeight;

    /** 年龄（岁） */
    private Integer age;

    /** 身高 cm */
    private BigDecimal height;

    /** 性别：male男 / female女 */
    @Pattern(regexp = "^(male|female)?$", message = "性别仅支持 male / female")
    private String gender;
}
