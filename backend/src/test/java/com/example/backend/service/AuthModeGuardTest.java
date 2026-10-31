package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.backend.config.AuthModeGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * AuthModeGuard 单测:selfdeploy profile 激活时三要素缺失启动即死;非 selfdeploy 形态零干扰。
 */
class AuthModeGuardTest {

    @Test
    void selfdeploy激活_任一要素缺失_启动即死() {
        // 全缺
        assertThrows(IllegalStateException.class, () -> run("selfdeploy", "", "", ""));
        // 各缺一项
        assertThrows(IllegalStateException.class, () -> run("selfdeploy", "", "secret", "token-secret"));
        assertThrows(IllegalStateException.class, () -> run("selfdeploy", "appid", "", "token-secret"));
        assertThrows(IllegalStateException.class, () -> run("selfdeploy", "appid", "secret", ""));
    }

    @Test
    void selfdeploy激活_三要素齐备_通过() {
        assertDoesNotThrow(() -> run("selfdeploy", "appid", "secret", "token-secret"));
    }

    @Test
    void 非selfdeploy形态_缺什么都不拦() {
        // 默认 profile(本地 H2)/cloud profile(头鉴权不需要 wx 配置):guard 静默
        assertDoesNotThrow(() -> run(null, "", "", ""));
        assertDoesNotThrow(() -> run("cloud", "", "", ""));
    }

    private void run(String activeProfile, String appid, String secret, String tokenSecret) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("wx.appid", appid);
        env.setProperty("wx.secret", secret);
        env.setProperty("auth.token-secret", tokenSecret);
        if (activeProfile != null) {
            env.setActiveProfiles(activeProfile);
        }
        new AuthModeGuard(env).check();
    }
}
