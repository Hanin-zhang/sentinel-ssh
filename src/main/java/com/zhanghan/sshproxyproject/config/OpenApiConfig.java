package com.zhanghan.sshproxyproject.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置
 * <p>
 * 文档页面：{@code /api/swagger-ui/index.html}
 * JSON 规范：{@code /api/v3/api-docs}
 * <p>
 * 配置了 {@code authorization} 请求头的 API Key 鉴权方案，
 * Swagger UI 右上角「Authorize」按钮粘贴登录 token 即可调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Authorization";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SSH 代理堡垒机 API 文档")
                        .description("SSH 代理堡垒机系统后端接口，包含认证、仪表盘、用户、审计、告警、会话管理等模块。")
                        .version("1.0.0")
                        .contact(new Contact().name("SSHProxy")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("authorization")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)));
    }
}
