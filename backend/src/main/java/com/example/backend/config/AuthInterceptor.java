package com.example.backend.config;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截:Authorization: Bearer {token} → TokenService.verify → CurrentUser。
 * token 形态由 auth.token-secret 决定(secret 空=legacy 明文 userId 演示态;非空=HMAC 签名)。
 * /api/auth/login、/api/invite/info、免鉴权组由 WebConfig 排除。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw ApiException.unauthorized();
        }
        // verify 内含形态校验与 HMAC 验签,非法即抛 unauthorized
        CurrentUser.set(tokenService.verify(auth.substring(7)));
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
