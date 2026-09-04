package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MVP 登录:code 即 openid(开发态/内测态)。
 * 正式上线替换为 code2session,token 替换为 session。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.param("请您先允许微信登录");
        }
        String openid = request.getCode().trim();
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
}
