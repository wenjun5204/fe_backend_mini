package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录身份解析(双部署形态,信任边界由 profile 决定,优先级从高到低):
 * 1. X-WX-OPENID 请求头 —— 仅 auth.trust-wx-header=true(cloud profile)时信任:
 *    微信云托管 callContainer 流量自动注入,平台验签不可伪造;
 * 2. request.code —— auth.dev-code-enabled=true(本地/测试)时 code 即 openid(dev 回退);
 * 3. 其余(自部署 selfdeploy profile)—— code 经微信 code2Session 换 openid(WeChatCode2SessionClient)。
 *
 * 安全前提:自部署=公网,X-WX-OPENID 可被任意客户端伪造,trust=false(文件层写死);
 * token 走 TokenService(secret 空=legacy 明文,自部署强制 HMAC)。
 */
@Service
public class AuthService {

    /** 微信云托管注入的用户身份头 */
    static final String WX_OPENID_HEADER = "X-WX-OPENID";

    private final UserRepository userRepository;
    private final WeChatCode2SessionClient code2SessionClient;
    private final TokenService tokenService;

    @Value("${auth.trust-wx-header:false}")
    private boolean trustWxHeader;

    @Value("${auth.dev-code-enabled:true}")
    private boolean devCodeEnabled;

    public AuthService(UserRepository userRepository,
                       WeChatCode2SessionClient code2SessionClient,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.code2SessionClient = code2SessionClient;
        this.tokenService = tokenService;
    }

    /** 单测注入鉴权模式(生产由 profile properties 决定) */
    void setAuthMode(boolean trustWxHeader, boolean devCodeEnabled) {
        this.trustWxHeader = trustWxHeader;
        this.devCodeEnabled = devCodeEnabled;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String openid = resolveOpenid(request, httpRequest);
        UserEntity user = findOrCreateUser(openid);

        LoginResponse resp = new LoginResponse();
        resp.setToken(tokenService.issue(user.getId()));
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        return resp;
    }

    /** openid 建用户;并发首登撞 openid 唯一索引时兜底重查(竞态加固) */
    private UserEntity findOrCreateUser(String openid) {
        return userRepository.findByOpenid(openid).orElseGet(() -> {
            try {
                UserEntity u = new UserEntity();
                u.setOpenid(openid);
                u.setNickname("福友" + (int) (Math.random() * 9000 + 1000));
                return userRepository.saveAndFlush(u);
            } catch (DataIntegrityViolationException e) {
                // 并发下另一请求已建同 openid 用户:重查复用
                return userRepository.findByOpenid(openid)
                        .orElseThrow(() -> new IllegalStateException("用户创建竞态兜底失败", e));
            }
        });
    }

    private String resolveOpenid(LoginRequest request, HttpServletRequest httpRequest) {
        // 1. 云托管链路:仅在 trust 模式下读头(自部署 trust=false,伪造头连解析都不进)
        if (trustWxHeader && httpRequest != null) {
            String wxOpenid = httpRequest.getHeader(WX_OPENID_HEADER);
            if (wxOpenid != null && !wxOpenid.isBlank()) {
                return wxOpenid.trim();
            }
        }
        String code = request == null || request.getCode() == null ? "" : request.getCode().trim();
        if (code.isEmpty()) {
            throw ApiException.param("请您先允许微信登录");
        }
        // 2. 开发态回退(本地/测试):code 即 openid;生产 profile 文件层写死 false
        if (devCodeEnabled) {
            return code;
        }
        // 3. 自部署标准链路:code2Session 换 openid
        return code2SessionClient.exchangeOpenid(code);
    }
}
