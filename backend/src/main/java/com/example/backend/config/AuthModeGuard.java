package com.example.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 自部署形态启动校验(fail-fast,对齐 CloudEnvPostProcessor 风格):
 * selfdeploy profile 激活时,code2Session 三要素(wx.appid/wx.secret/auth.token-secret)任一为空 → 启动即死。
 * 目的:公网部署下明文 userId token 可被遍历冒充、缺 secret 则 code2Session 必失败——
 * 宁可启动失败,绝不带弱配置上线。
 */
@Component
public class AuthModeGuard {

    private final Environment environment;

    public AuthModeGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("selfdeploy"))) {
            return;
        }
        require("wx.appid", "WX_APP_ID");
        require("wx.secret", "WX_APP_SECRET");
        require("auth.token-secret", "AUTH_TOKEN_SECRET");
    }

    private void require(String property, String envName) {
        String value = environment.getProperty(property, "");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "selfdeploy profile 已激活但缺少配置 " + property + "(环境变量 " + envName + ")"
                            + "——公网部署严禁以弱配置启动,请补齐后重启");
        }
    }
}
