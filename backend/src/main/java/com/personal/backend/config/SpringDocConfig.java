package com.personal.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 接口文档配置
 * - 访问：/swagger-ui/index.html（Swagger UI）；/v3/api-docs（OpenAPI JSON）
 * - 全局 Bearer 认证：右上角 Authorize 粘贴 accessToken 后可在线试调需登录接口
 * - 文档端点不在 /api/** 拦截范围内，无需登录即可浏览
 */
@Configuration
public class SpringDocConfig {

    private static final String SECURITY_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("个人记录系统 API")
                        .description("记账 / 健康 / 锻炼 / 饮食 / 学习 / 每日总结 全模块接口文档。"
                                + "需登录接口先调 /api/auth/login 获取 accessToken，再点右上角 Authorize 填入即可试调。")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(SECURITY_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_NAME));
    }
}
