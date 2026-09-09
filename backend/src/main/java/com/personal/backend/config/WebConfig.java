package com.personal.backend.config;

import com.personal.backend.interceptor.AuthInterceptor;
import com.personal.backend.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 配置：拦截器注册、跨域、静态资源（本地文件）映射
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${app.upload-path}")
    private String uploadPath;

    /** 注册认证拦截器（白名单接口不校验）+ 限流拦截器（@RateLimit 注解方法按 IP 限流） */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/admin/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout"
                );
        // 限流拦截器：登录/注册/上传等注解方法生效（/api/** 内）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }

    /** 本地文件存储映射：/uploads/** -> 磁盘 uploads 目录 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }

    /**
     * 跨域配置（v2.5.4 收紧）：仅放行本机双端口前端。
     * 双端页面均走 Vite 代理（同源），公网隧道也经代理转发——浏览器直连 8080 的跨源请求
     * 在当前架构下不存在，白名单仅作纵深防御（防任意网页带 Cookie 调 /api 读响应）。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:5173", "http://localhost:5174",
                        "http://127.0.0.1:5173", "http://127.0.0.1:5174")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
