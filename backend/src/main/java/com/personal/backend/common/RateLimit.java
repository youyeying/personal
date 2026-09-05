package com.personal.backend.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：同一 IP 每分钟最多 maxPerMinute 次
 * 用于登录 / 注册 / 上传等易被暴力刷的入口（429 业务异常提示）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大次数 */
    int maxPerMinute();
}
