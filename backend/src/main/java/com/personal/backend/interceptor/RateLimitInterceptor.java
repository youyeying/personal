package com.personal.backend.interceptor;

import com.personal.backend.common.BizException;
import com.personal.backend.common.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流拦截器：按「IP + 方法」维度固定窗口计数（60s 窗口）
 * - 方法需标注 @RateLimit(maxPerMinute = N)
 * - 超限抛 429 业务异常（全局异常处理器统一返回）
 * - 桶数量 = IP × 限流接口数，个人项目规模可忽略；窗口过期时原地重置
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** key = ip:方法签名 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 计数桶：窗口起点 + 计数器（compute 内单线程初始化/重置，计数用原子量） */
    private static final class Bucket {
        final AtomicLong count = new AtomicLong();
        volatile long windowStart;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit limit = method.getMethodAnnotation(RateLimit.class);
        if (limit == null) {
            return true;
        }
        String key = request.getRemoteAddr() + ":" + method.getMethod().toGenericString();
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.compute(key, (k, old) -> {
            if (old == null || now - old.windowStart > 60_000) {
                Bucket b = new Bucket();
                b.windowStart = now;
                return b;
            }
            return old;
        });
        if (bucket.count.incrementAndGet() > limit.maxPerMinute()) {
            throw new BizException(429, "操作过于频繁，请稍后再试");
        }
        return true;
    }
}
