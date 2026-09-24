package com.example.backend.config;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MVP 鉴权:Authorization: Bearer {token},token 即 userId(演示态)。
 * 内测前替换为 openid→session。/api/invite/info 与 /api/auth/login 免鉴权。
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw ApiException.unauthorized();
        }
        try {
            CurrentUser.set(Long.parseLong(auth.substring(7).trim()));
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized();
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
