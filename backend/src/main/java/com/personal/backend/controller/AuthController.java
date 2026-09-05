package com.personal.backend.controller;

import com.personal.backend.common.RateLimit;
import com.personal.backend.common.Result;
import com.personal.backend.dto.*;
import com.personal.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口：注册 / 登录 / 刷新令牌 / 登出 / 个人信息 / 修改密码
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（限流：每 IP 每分钟 3 次） */
    @RateLimit(maxPerMinute = 3)
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request), "注册成功");
    }

    /** 登录（写 refresh Cookie + 返回 accessToken；限流防暴力试密码） */
    @RateLimit(maxPerMinute = 5)
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse response) {
        return Result.ok(authService.login(request, httpRequest, response), "登录成功");
    }

    /** 刷新 accessToken（用 httpOnly Cookie 里的 refresh token，滚动续期） */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        return Result.ok(authService.refresh(request, response));
    }

    /** 登出（删除会话 + 清 Cookie） */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return Result.ok(null, "已退出登录");
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me());
    }

    /** 修改个人信息 */
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(authService.updateProfile(request), "修改成功");
    }

    /** 修改密码（成功后撤销全部会话，需重新登录） */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletResponse response) {
        authService.changePassword(request, response);
        return Result.ok(null, "密码修改成功");
    }
}
