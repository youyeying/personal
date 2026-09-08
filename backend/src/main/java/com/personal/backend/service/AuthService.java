package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.LoginUser;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.*;
import com.personal.backend.entity.AdminUser;
import com.personal.backend.entity.AuthSession;
import com.personal.backend.entity.User;
import com.personal.backend.mapper.AdminUserMapper;
import com.personal.backend.mapper.AuthSessionMapper;
import com.personal.backend.mapper.UserMapper;
import com.personal.backend.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证 Service：注册 / 登录 / 个人信息 / 修改密码 / 会话刷新 / 登出
 *
 * 鉴权方案（双 token）：
 * - accessToken：JWT 短效（15 分钟），前端内存持有，走 Authorization: Bearer
 * - refreshToken：随机不透明串，存 httpOnly Cookie，哈希落库（滚动 24h）
 *   刷新时 rotation（换新并作废旧），会话在独立 auth_session 表、不占用 user 表
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户类型常量：1=业务用户 user 表 / 2=开发账号 admin_user 表 */
    private static final int USER_TYPE_USER = 1;
    private static final int USER_TYPE_ADMIN = 2;

    private final UserMapper userMapper;
    private final AdminUserMapper adminUserMapper;
    private final AuthSessionMapper authSessionMapper;
    private final JwtUtils jwtUtils;
    private final OperationLogService operationLogService;
    private final FileService fileService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.session.expire-hours}")
    private long sessionExpireHours;

    @Value("${app.session.cookie-secure}")
    private boolean cookieSecure;

    /** /auth/refresh 限流：同一 IP 每分钟最多尝试次数（防暴力） */
    private static final int REFRESH_MAX_PER_MINUTE = 60;
    private final Map<String, long[]> refreshRate = new ConcurrentHashMap<>();

    /** refresh Cookie 名称：区分站点会话（本地双端口同域 localhost，同名 Cookie 会互相覆盖导致串号） */
    private static final String REFRESH_COOKIE_USER = "refresh_token";
    private static final String REFRESH_COOKIE_ADMIN = "refresh_token_admin";
    /** 全部 Cookie 名（登出/清理时都清） */
    private static final String[] ALL_REFRESH_COOKIES = {REFRESH_COOKIE_USER, REFRESH_COOKIE_ADMIN};

    /** 按用户类型取 refresh Cookie 名 */
    private static String refreshCookieName(int userType) {
        return userType == USER_TYPE_ADMIN ? REFRESH_COOKIE_ADMIN : REFRESH_COOKIE_USER;
    }

    /**
     * 注册：创建用户（模板数据 user_id=0 全局共享，无需复制；查询时与本人自定义取并集）。
     * 不自动登录、不写会话/Cookie（注册后由用户走登录，避免产生孤儿会话）
     */
    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        Long countByName = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (countByName > 0) {
            throw new BizException("当前已有此用户");
        }
        Long countByPhone = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone()));
        if (countByPhone > 0) {
            throw new BizException("当前手机号已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        userMapper.insert(user);

        operationLogService.record(user.getId(), "USER", "REGISTER", user.getId(),
                "注册账号：" + request.getUsername());

        return Map.of("userInfo", toUserInfo(user));
    }

    /**
     * 登录：校验用户名 + 密码，创建/复用会话 + 写 refresh Cookie，签发 accessToken
     */
    public Map<String, Object> login(LoginRequest request, HttpServletRequest httpRequest,
                                     HttpServletResponse response) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException("当前用户名不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("密码错误");
        }

        createSession(user.getId(), USER_TYPE_USER, httpRequest, response);
        operationLogService.record(user.getId(), "USER", "LOGIN", user.getId(),
                "用户登录：" + user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtUtils.generateToken(user.getId(), user.getUsername(), USER_TYPE_USER));
        result.put("userType", USER_TYPE_USER);
        result.put("userInfo", toUserInfo(user));
        return result;
    }

    /**
     * 开发账号登录（管理端）：校验 admin_user 表账号密码，写会话（user_type=2）+ 签发 accessToken
     */
    public Map<String, Object> adminLogin(LoginRequest request, HttpServletRequest httpRequest,
                                          HttpServletResponse response) {
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, request.getUsername()));
        if (admin == null) {
            throw new BizException("开发账号不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BizException("密码错误");
        }

        createSession(admin.getId(), USER_TYPE_ADMIN, httpRequest, response);
        operationLogService.record(admin.getId(), "ADMIN", "LOGIN", admin.getId(),
                "开发账号登录：" + admin.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtUtils.generateToken(admin.getId(), admin.getUsername(), USER_TYPE_ADMIN));
        result.put("userType", USER_TYPE_ADMIN);
        result.put("userInfo", toAdminInfo(admin));
        return result;
    }

    /**
     * 刷新 accessToken：校验 Cookie 里的 refresh token → rotation → 签发新 accessToken + 滚动续期
     */
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        rateLimit(request);

        // 按调用站点严格取对应 refresh Cookie：前端 refreshAccessToken 带 ?site=admin（开发端）或 site=user/缺省（用户端）
        // 独立 Cookie 名隔离两站会话，杜绝 localhost 双端口同域串号
        String site = request.getParameter("site");
        String refreshToken = readCookie(request, "admin".equals(site) ? REFRESH_COOKIE_ADMIN : REFRESH_COOKIE_USER);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(401, "登录已过期，请重新登录");
        }

        AuthSession session = findByHash(hash(refreshToken));
        if (session == null) {
            // 并发刷新宽容：多标签页各自持同一 refresh_token 刷新时，先到的已 rotation 作废旧 token，
            // 后到的若直接判过期会把正常用户踢出登录。这里按「同一设备 60 秒内刚更新过的会话」合并：
            // 仍持有效会话则续期，仅真正无会话/全过期才判 401。
            // 关键：宽容查询必须按「调用站点的 userType」过滤——用户端/开发端在同一浏览器同 UA，
            // 若不限 userType，一端刷新后 60s 内另一端刷新会命中对方的会话、拿到错误用户类型的 token 被踢。
            String ua = request.getHeader("User-Agent");
            String deviceKey = hash(ua != null ? ua : "");
            int expectType = "admin".equals(site) ? USER_TYPE_ADMIN : USER_TYPE_USER;
            session = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                    .eq(AuthSession::getUserType, expectType)
                    .eq(AuthSession::getDeviceKey, deviceKey)
                    .ge(AuthSession::getUpdatedAt, LocalDateTime.now().minusSeconds(60))
                    .orderByDesc(AuthSession::getUpdatedAt)
                    .last("LIMIT 1"));
        }
        if (session == null || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(401, "登录已过期，请重新登录");
        }

        // rotation：换新 refresh token + 滚动到期，旧 token 立即失效（按会话类型写对应 Cookie）
        String newToken = randomToken();
        session.setRefreshTokenHash(hash(newToken));
        session.setExpiresAt(LocalDateTime.now().plusHours(sessionExpireHours));
        session.setUpdatedAt(LocalDateTime.now());
        authSessionMapper.updateById(session);
        int sessionType = session.getUserType() == null ? USER_TYPE_USER : session.getUserType();
        setRefreshCookie(response, refreshCookieName(sessionType), newToken);

        // 按会话用户类型路由到 user / admin_user 表，签发带 userType 的新 accessToken
        Integer userType = session.getUserType() == null ? USER_TYPE_USER : session.getUserType();
        if (userType == USER_TYPE_ADMIN) {
            AdminUser admin = adminUserMapper.selectById(session.getUserId());
            if (admin == null) {
                throw new BizException(401, "登录已过期，请重新登录");
            }
            return Map.of("accessToken", jwtUtils.generateToken(admin.getId(), admin.getUsername(), USER_TYPE_ADMIN),
                    "userType", USER_TYPE_ADMIN);
        }
        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        return Map.of("accessToken", jwtUtils.generateToken(user.getId(), user.getUsername(), USER_TYPE_USER),
                "userType", USER_TYPE_USER);
    }

    /**
     * 登出：删除当前会话 + 清 Cookie
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // 清两枚 Cookie（user/admin）对应的会话，防站点会话残留
        for (String cookieName : ALL_REFRESH_COOKIES) {
            String refreshToken = readCookie(request, cookieName);
            if (!StringUtils.hasText(refreshToken)) {
                continue;
            }
            AuthSession session = findByHash(hash(refreshToken));
            if (session != null) {
                session.setUpdatedAt(LocalDateTime.now());
                authSessionMapper.deleteById(session.getId()); // 置逻辑删除
            }
        }
        clearRefreshCookie(response);
    }

    /**
     * 获取当前登录用户信息（按会话用户类型路由到 user / admin_user 表）
     */
    public Map<String, Object> me() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        if (loginUser.getUserType() != null && loginUser.getUserType() == USER_TYPE_ADMIN) {
            AdminUser admin = adminUserMapper.selectById(loginUser.getId());
            if (admin == null) {
                throw new BizException(401, "用户不存在或已注销");
            }
            return Map.of("userType", USER_TYPE_ADMIN, "userInfo", toAdminInfo(admin));
        }
        User user = getUserById(loginUser.getId());
        return Map.of("userType", USER_TYPE_USER, "userInfo", toUserInfo(user));
    }

    /**
     * 修改个人信息
     */
    public Map<String, Object> updateProfile(UpdateProfileRequest request) {
        User user = getUserById(UserContext.requireUserId());

        String oldAvatar = user.getAvatar();

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, request.getPhone())
                            .ne(User::getId, user.getId()));
            if (count > 0) {
                throw new BizException("手机号已被占用");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getTargetWeight() != null) {
            user.setTargetWeight(request.getTargetWeight());
        }
        if (Boolean.TRUE.equals(request.getClearTargetWeight())) {
            user.setTargetWeight(null);
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getHeight() != null) {
            user.setHeight(request.getHeight());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getDietTargetGap() != null) {
            Integer gap = request.getDietTargetGap();
            if (gap < -9999 || gap > 3000) {
                throw new BizException("目标缺口需在 -9999~3000 之间（0=维持，负=增肌，1000~1500=快速减脂）");
            }
            user.setDietTargetGap(gap);
        }
        userMapper.updateById(user);

        if (oldAvatar != null && !oldAvatar.equals(user.getAvatar())) {
            fileService.deleteAvatar(oldAvatar);
        }

        operationLogService.record("USER", "UPDATE", user.getId(), "修改个人信息");

        return Map.of("userInfo", toUserInfo(user));
    }

    /**
     * 修改密码：原密码校验 + 一月冷却（首次修改免限，改后开始计冷却）。
     * 改密后撤销该用户全部会话，强制重新登录
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, HttpServletResponse response) {
        User user = getUserById(UserContext.requireUserId());

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码错误");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }

        if (user.getPasswordUpdatedAt() != null) {
            LocalDateTime nextAllowed = user.getPasswordUpdatedAt().plusMonths(1);
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                throw new BizException("距上次修改密码不足一个月，暂不能修改");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 撤销该用户所有会话，强制重新登录
        authSessionMapper.delete(
                new LambdaQueryWrapper<AuthSession>().eq(AuthSession::getUserId, user.getId()));
        clearRefreshCookie(response);

        operationLogService.record("USER", "UPDATE", user.getId(), "修改密码");
    }

    // ===================== 会话 / Cookie 私有方法 =====================

    /** 创建或复用会话并写 Cookie：按 用户+设备指纹 复用同一设备的有效会话，避免行随登录次数增长 */
    private void createSession(Long userId, Integer userType, HttpServletRequest request, HttpServletResponse response) {
        String ua = request.getHeader("User-Agent");
        String deviceKey = hash(ua != null ? ua : "");
        LocalDateTime now = LocalDateTime.now();
        String token = randomToken();

        // 清理该用户已过期会话，避免历史行堆积
        authSessionMapper.delete(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId)
                .lt(AuthSession::getExpiresAt, now));

        // 复用该设备未过期会话（同一浏览器反复登录只保一行）；没有则新增
        AuthSession session = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId)
                .eq(AuthSession::getDeviceKey, deviceKey)
                .gt(AuthSession::getExpiresAt, now));
        if (session == null) {
            session = new AuthSession();
            session.setUserId(userId);
            session.setDeviceKey(deviceKey);
            session.setDeviceName(parseDeviceName(ua));
        }
        session.setUserType(userType == null ? USER_TYPE_USER : userType);
        session.setRefreshTokenHash(hash(token));
        session.setExpiresAt(now.plusHours(sessionExpireHours));
        if (session.getId() == null) {
            authSessionMapper.insert(session);
        } else {
            session.setUpdatedAt(now);
            authSessionMapper.updateById(session);
        }
        setRefreshCookie(response, refreshCookieName(userType == null ? USER_TYPE_USER : userType), token);
    }

    /** 从 User-Agent 简单解析出可读设备名（浏览器 · 系统），仅展示用 */
    private String parseDeviceName(String ua) {
        if (!StringUtils.hasText(ua)) {
            return "未知设备";
        }
        String browser;
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("OPR/") || ua.contains("Opera")) {
            browser = "Opera";
        } else if (ua.contains("Chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("Firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("Safari/")) {
            browser = "Safari";
        } else {
            browser = "其他浏览器";
        }
        String os;
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("Mac OS X")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else {
            os = "其他系统";
        }
        return browser + " · " + os;
    }

    private AuthSession findByHash(String tokenHash) {
        return authSessionMapper.selectOne(
                new LambdaQueryWrapper<AuthSession>().eq(AuthSession::getRefreshTokenHash, tokenHash));
    }

    private void setRefreshCookie(HttpServletResponse response, String cookieName, String value) {
        Cookie cookie = new Cookie(cookieName, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge((int) (sessionExpireHours * 3600));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** 清全部 refresh Cookie（user/admin 两枚） */
    private void clearRefreshCookie(HttpServletResponse response) {
        for (String cookieName : ALL_REFRESH_COOKIES) {
            Cookie cookie = new Cookie(cookieName, "");
            cookie.setHttpOnly(true);
            cookie.setSecure(cookieSecure);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    /** Origin 校验：仅允许与请求 Host 同源（防跨站调用刷新接口） */
    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String host = request.getHeader("Host");
        // SameSite=Lax 已拦截跨站 Cookie 携带；此处再做一层同源校验兜底
        if (StringUtils.hasText(origin) && StringUtils.hasText(host)) {
            String originHost = java.net.URI.create(origin).getHost();
            String hostName = host.split(":")[0];
            if (originHost == null || !originHost.equalsIgnoreCase(hostName)) {
                throw new BizException("非法的跨域请求");
            }
        }
    }

    /** 简单限流：同一 IP 每分钟最多 REFRESH_MAX_PER_MINUTE 次 */
    private void rateLimit(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] bucket = refreshRate.computeIfAbsent(ip, k -> new long[]{0, now});
        synchronized (bucket) {
            if (now - bucket[1] > 60_000) {
                bucket[0] = 0;
                bucket[1] = now;
            }
            bucket[0]++;
            if (bucket[0] > REFRESH_MAX_PER_MINUTE) {
                throw new BizException(429, "操作过于频繁，请稍后再试");
            }
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 十六进制 */
    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("会话处理失败");
        }
    }

    /** 按 id 查用户，不存在抛异常 */
    private User getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(401, "用户不存在或已注销");
        }
        return user;
    }

    /** 脱敏返回开发账号信息（不返回密码） */
    private Map<String, Object> toAdminInfo(AdminUser admin) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", admin.getId());
        info.put("username", admin.getUsername());
        info.put("phone", admin.getPhone());
        info.put("nickname", admin.getNickname());
        info.put("avatar", admin.getAvatar());
        info.put("createdAt", admin.getCreatedAt());
        return info;
    }

    /** 脱敏返回用户信息（不返回密码） */
    private Map<String, Object> toUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("phone", user.getPhone());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("targetWeight", user.getTargetWeight());
        info.put("age", user.getAge());
        info.put("height", user.getHeight());
        info.put("gender", user.getGender());
        info.put("dietTargetGap", user.getDietTargetGap());
        info.put("passwordUpdatedAt", user.getPasswordUpdatedAt());
        info.put("createdAt", user.getCreatedAt());
        return info;
    }
}