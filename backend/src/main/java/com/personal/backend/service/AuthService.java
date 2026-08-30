package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.dto.*;
import com.personal.backend.entity.AuthSession;
import com.personal.backend.entity.ExerciseItem;
import com.personal.backend.entity.ExpenseCategory;
import com.personal.backend.entity.User;
import com.personal.backend.mapper.AuthSessionMapper;
import com.personal.backend.mapper.ExerciseItemMapper;
import com.personal.backend.mapper.ExpenseCategoryMapper;
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

    private final UserMapper userMapper;
    private final AuthSessionMapper authSessionMapper;
    private final ExpenseCategoryMapper expenseCategoryMapper;
    private final ExerciseItemMapper exerciseItemMapper;
    private final JwtUtils jwtUtils;
    private final OperationLogService operationLogService;
    private final FileService fileService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.session.expire-hours}")
    private long sessionExpireHours;

    @Value("${app.session.cookie-secure}")
    private boolean cookieSecure;

    /** /auth/refresh 限流：同一 IP 每分钟最多尝试次数（防暴力） */
    private static final int REFRESH_MAX_PER_MINUTE = 20;
    private final Map<String, long[]> refreshRate = new ConcurrentHashMap<>();

    /** refresh Cookie 名称 */
    private static final String REFRESH_COOKIE = "refresh_token";

    /**
     * 注册：创建用户 + 复制默认分类。
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

        copyDefaultCategories(user.getId());
        copyDefaultExercises(user.getId());

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

        createSession(user.getId(), httpRequest, response);
        operationLogService.record(user.getId(), "USER", "LOGIN", user.getId(),
                "用户登录：" + user.getUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtUtils.generateToken(user.getId(), user.getUsername()));
        result.put("userInfo", toUserInfo(user));
        return result;
    }

    /**
     * 刷新 accessToken：校验 Cookie 里的 refresh token → rotation → 签发新 accessToken + 滚动续期
     */
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        rateLimit(request);

        String refreshToken = readCookie(request, REFRESH_COOKIE);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(401, "登录已过期，请重新登录");
        }

        AuthSession session = findByHash(hash(refreshToken));
        if (session == null || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(401, "登录已过期，请重新登录");
        }

        // rotation：换新 refresh token + 滚动到期，旧 token 立即失效
        String newToken = randomToken();
        session.setRefreshTokenHash(hash(newToken));
        session.setExpiresAt(LocalDateTime.now().plusHours(sessionExpireHours));
        session.setUpdatedAt(LocalDateTime.now());
        authSessionMapper.updateById(session);
        setRefreshCookie(response, newToken);

        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        return Map.of("accessToken", jwtUtils.generateToken(session.getUserId(), user.getUsername()));
    }

    /**
     * 登出：删除当前会话 + 清 Cookie
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, REFRESH_COOKIE);
        if (StringUtils.hasText(refreshToken)) {
            AuthSession session = findByHash(hash(refreshToken));
            if (session != null) {
                session.setUpdatedAt(LocalDateTime.now());
                authSessionMapper.deleteById(session.getId()); // 置逻辑删除
            }
        }
        clearRefreshCookie(response);
    }

    /**
     * 获取当前登录用户信息
     */
    public Map<String, Object> me() {
        User user = getUserById(UserContext.requireUserId());
        return Map.of("userInfo", toUserInfo(user));
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
    private void createSession(Long userId, HttpServletRequest request, HttpServletResponse response) {
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
        session.setRefreshTokenHash(hash(token));
        session.setExpiresAt(now.plusHours(sessionExpireHours));
        if (session.getId() == null) {
            authSessionMapper.insert(session);
        } else {
            session.setUpdatedAt(now);
            authSessionMapper.updateById(session);
        }
        setRefreshCookie(response, token);
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

    private void setRefreshCookie(HttpServletResponse response, String value) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge((int) (sessionExpireHours * 3600));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
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

    /** 复制初始用户的默认分类到新用户 */
    private void copyDefaultCategories(Long newUserId) {
        List<ExpenseCategory> defaults = expenseCategoryMapper.selectList(
                new LambdaQueryWrapper<ExpenseCategory>()
                        .eq(ExpenseCategory::getUserId, 1L)
                        .orderByAsc(ExpenseCategory::getSortOrder));
        for (ExpenseCategory c : defaults) {
            ExpenseCategory copy = new ExpenseCategory();
            copy.setUserId(newUserId);
            copy.setName(c.getName());
            copy.setType(c.getType());
            copy.setSortOrder(c.getSortOrder());
            expenseCategoryMapper.insert(copy);
        }
    }

    /** 复制初始用户的默认锻炼动作到新用户（注册即可用，无需自建动作） */
    private void copyDefaultExercises(Long newUserId) {
        List<ExerciseItem> defaults = exerciseItemMapper.selectList(
                new LambdaQueryWrapper<ExerciseItem>()
                        .eq(ExerciseItem::getUserId, 1L)
                        .orderByAsc(ExerciseItem::getSortOrder));
        for (ExerciseItem e : defaults) {
            ExerciseItem copy = new ExerciseItem();
            copy.setUserId(newUserId);
            copy.setName(e.getName());
            copy.setType(e.getType());
            copy.setBaseMet(e.getBaseMet());
            copy.setRefSpeed(e.getRefSpeed());
            copy.setHasWeight(e.getHasWeight());
            copy.setHasHand(e.getHasHand());
            copy.setSortOrder(e.getSortOrder());
            exerciseItemMapper.insert(copy);
        }
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
        info.put("passwordUpdatedAt", user.getPasswordUpdatedAt());
        info.put("createdAt", user.getCreatedAt());
        return info;
    }
}