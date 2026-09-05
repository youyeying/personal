package com.personal.backend.interceptor;

import com.personal.backend.common.BizException;
import com.personal.backend.common.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * RateLimitInterceptor 单元测试（mock HttpServletRequest / HandlerMethod）
 * 覆盖：60s 窗口内计数超限抛 429、未标注解直通、非 HandlerMethod 直通
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private RateLimitInterceptor interceptor;

    /** 测试目标方法（标 @RateLimit(3)） */
    @RateLimit(maxPerMinute = 3)
    void limitedEndpoint() {
    }

    /** 未限流方法 */
    void unlimitedEndpoint() {
    }

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor();
    }

    /** 让 request 返回指定 IP（按需 stub，避免 strict mock 的 UnnecessaryStubbing） */
    private void fromIp(String ip) {
        when(request.getRemoteAddr()).thenReturn(ip);
    }

    private HandlerMethod handler(String methodName) throws Exception {
        Method method = RateLimitInterceptorTest.class.getDeclaredMethod(methodName);
        return new HandlerMethod(this, method);
    }

    @Test
    @DisplayName("窗口内未超限：前 3 次全部放行")
    void allowsWithinLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() ->
                    interceptor.preHandle(request, response, handler("limitedEndpoint")));
        }
    }

    @Test
    @DisplayName("同一 IP 超过 maxPerMinute：第 4 次抛 429")
    void rejectsWhenExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler("limitedEndpoint"));
        }
        assertThrows(BizException.class, () ->
                interceptor.preHandle(request, response, handler("limitedEndpoint")));
    }

    @Test
    @DisplayName("未标注 @RateLimit 的方法直通")
    void ignoresUnannotatedMethod() throws Exception {
        assertDoesNotThrow(() ->
                interceptor.preHandle(request, response, handler("unlimitedEndpoint")));
        // 多次调用也不拦截
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() ->
                    interceptor.preHandle(request, response, handler("unlimitedEndpoint")));
        }
    }

    @Test
    @DisplayName("非 HandlerMethod（静态资源等）直通")
    void ignoresNonHandlerMethod() {
        assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    @DisplayName("不同 IP 计数互不影响")
    void isolatesByIp() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler("limitedEndpoint"));
        }
        // 换 IP：同方法仍有完整额度
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        assertDoesNotThrow(() ->
                interceptor.preHandle(request, response, handler("limitedEndpoint")));
    }
}
