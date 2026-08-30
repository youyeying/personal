package com.personal.backend.common;

import lombok.Getter;

/**
 * 业务异常：业务规则校验不通过时抛出，由全局异常处理器转为统一响应
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码，默认 400 */
    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
