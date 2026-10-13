package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录身份解析(优先级从高到低):
 * 1. X-WX-OPENID 请求头 —— 微信云托管 callContainer 流量自动注入,平台验签不可伪造(生产链路);
 * 2. request.code —— 本地/开发者工具联调回退(code 即 openid,仅限不公网暴露的内网后端)。
 *
 * 后续 token 机制不变:Authorization Bearer {userId}。云托管服务不要开启公网访问路径,
 * 否则 X-WX-OPENID 可被外部伪造。
 */
@Service
public class AuthService {

    /** 微信云托管注入的用户身份头 */
    static final String WX_OPENID_HEADER = "X-WX-OPENID";

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String openid = resolveOpenid(request, httpRequest);
        UserEntity user = userRepository.findByOpenid(openid).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setOpenid(openid);
            u.setNickname("福友" + (int) (Math.random() * 9000 + 1000));
            return userRepository.save(u);
        });

        LoginResponse resp = new LoginResponse();
        resp.setToken(String.valueOf(user.getId()));
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        return resp;
    }

    private String resolveOpenid(LoginRequest request, HttpServletRequest httpRequest) {
        String wxOpenid = httpRequest != null ? httpRequest.getHeader(WX_OPENID_HEADER) : null;
        if (wxOpenid != null && !wxOpenid.isBlank()) {
            return wxOpenid.trim();
        }
        // 本地联调回退:code 即 openid(仅限内网后端,生产流量必须来自 callContainer)
        if (request != null && request.getCode() != null && !request.getCode().isBlank()) {
            return request.getCode().trim();
        }
        throw ApiException.param("请您先允许微信登录");
    }
}
