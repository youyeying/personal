package com.personal.backend.common;

import lombok.Data;

/**
 * 统一响应结构：{ code, message, data }
 */
@Data
public class Result<T> {

    /** 业务状态码：200 成功 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> ok() {
        return build(200, "操作成功", null);
    }

    public static <T> Result<T> ok(T data) {
        return build(200, "操作成功", data);
    }

    public static <T> Result<T> ok(T data, String message) {
        return build(200, message, data);
    }

    public static <T> Result<T> fail(String message) {
        return build(400, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(Integer code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
