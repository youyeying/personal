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
            "/api/auth/admin/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    /** 开发向接口路径前缀：仅开发账号（user_type=2）可访问（不带尾斜杠，配合下方严格匹配兼容无子路径的列表接口） */
    private static final String[] ADMIN_PREFIXES = {"/api/dev", "/api/operation-logs", "/api/admin"};

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
            Integer userType = claims.get("userType", Integer.class);
            if (userType == null) userType = 1; // 旧 token 兜底：业务用户
            UserContext.set(new LoginUser(userId, username, userType));

            // 接口隔离：开发向接口仅 user_type=2；其余业务接口仅 user_type=1
            //（/api/auth/me|profile|password 两种类型均可用，me 内部按 userType 路由）
            // 严格匹配：精确等于前缀，或前缀后紧跟 '/'（避免 /api/dev-xxx 误判）
            boolean isAdminApi = false;
            for (String prefix : ADMIN_PREFIXES) {
                if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                    isAdminApi = true;
                    break;
                }
            }
            if (isAdminApi) {
                UserContext.requireAdmin();
            } else if (!uri.startsWith("/api/auth/")) {
                UserContext.requireBusinessUser();
            }
            return true;
        } catch (Exception e) {
            if (e instanceof BizException) {
                throw (BizException) e;
            }
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
