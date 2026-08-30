package com.personal.backend.interceptor;

import com.personal.backend.common.BizException;
import com.personal.backend.common.LoginUser;
import com.personal.backend.common.UserContext;
import com.personal.backend.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * JWT 认证拦截器：拦截 /api/**（白名单接口除外）
 * - 校验 Authorization: Bearer <token>
 * - 解析成功后将当前用户放入 UserContext
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 免认证接口白名单（与 WebConfig excludePathPatterns 双保险） */
    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 白名单接口免认证
        String uri = request.getRequestURI();
        if (WHITE_LIST.contains(uri)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(401, "未登录或登录已过期");
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = jwtUtils.getUserId(claims);
            String username = claims.get("username", String.class);
            UserContext.set(new LoginUser(userId, username));
            return true;
        } catch (Exception e) {
            throw new BizException(401, "未登录或登录已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束清理上下文，防止线程复用串号
        UserContext.clear();
    }

    /** 从请求头解析 Bearer Token */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
