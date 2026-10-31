package com.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/api/**")
                // "/api/photos/*" Ant 单段:放行 /api/photos/{photoId}(回图免鉴权),
                // 不放行 /api/photos 本身(POST 上传仍鉴权)
                .excludePathPatterns("/api/auth/login", "/api/invite/info", "/api/ping",
                        "/api/visitor/**", "/api/photos/*",
                        // 值班分享只读月历(契约 v1.9.1):/api/duty/shared/{scene} 免鉴权
                        "/api/duty/shared/*");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        // frontend/ React 演示端联调用;小程序无跨域问题
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
