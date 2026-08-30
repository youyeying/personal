package com.personal.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 功能变更记录请求
 */
@Data
public class FeatureLogRequest {

    /** 变更类型：新增/修改/删除/修复 */
    @NotBlank(message = "变更类型不能为空")
    @Pattern(regexp = "新增|修改|删除|修复", message = "变更类型仅支持：新增/修改/删除/修复")
    private String type;

    /** 所属模块：记账/健康/学习/系统/其他 */
    @NotBlank(message = "所属模块不能为空")
    @Size(max = 20, message = "模块名最长 20 字")
    private String module;

    /** 功能变更描述 */
    @NotBlank(message = "功能变更描述不能为空")
    @Size(max = 500, message = "功能变更描述最长 500 字")
    private String content;
}
