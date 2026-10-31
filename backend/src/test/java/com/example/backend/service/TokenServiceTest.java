package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.backend.common.ApiException;
import org.junit.jupiter.api.Test;

/**
 * TokenService 单测:legacy 明文降级(secret 空)/ HMAC 签发与验签 / 篡改拒绝 / 形态与 secret 配置不符拒绝。
 */
class TokenServiceTest {

    private TokenService service(String secret) {
        TokenService s = new TokenService();
        s.setSecret(secret);
        return s;
    }

    @Test
    void secret为空_签发legacy明文token_纯数字可验回() {
        TokenService s = service("");
        String token = s.issue(42L);
        assertEquals("42", token);
        assertEquals(42L, s.verify("42"));
        // 任一历史明文 userId 均可验回(存量兼容)
        assertEquals(7L, s.verify("7"));
    }

    @Test
    void secret非空_签发HMAC形态_验签回userId() {
        TokenService s = service("test-secret");
        String token = s.issue(123L);
        // 形态:1.{userId}.{32位hex}
        assertEquals(true, token.matches("^1\\.123\\.[0-9a-f]{32}$"), "token=" + token);
        assertEquals(123L, s.verify(token));
    }

    @Test
    void secret非空_篡改userId或HMAC_拒绝() {
        TokenService s = service("test-secret");
        String token = s.issue(123L);
        // 篡改 userId(用 123 的签名冒充 124)
        String forgedUser = token.replace("1.123.", "1.124.");
        assertThrows(ApiException.class, () -> s.verify(forgedUser));
        // 篡改 HMAC 尾段
        String tail = token.substring(token.length() - 1);
        String flipped = ("0".equals(tail) ? "1" : "0");
        String forgedSig = token.substring(0, token.length() - 1) + flipped;
        assertThrows(ApiException.class, () -> s.verify(forgedSig));
        // 截断 HMAC
        assertThrows(ApiException.class, () -> s.verify(token.substring(0, token.length() - 1)));
    }

    @Test
    void secret非空_收到legacy明文token_拒绝() {
        TokenService s = service("test-secret");
        // 配了 secret 后明文 token 必须失效(攻击者不能再遍历 Bearer 1..N)
        assertThrows(ApiException.class, () -> s.verify("2"));
        assertThrows(ApiException.class, () -> s.verify("1.2." + "a".repeat(32)));
        // 密钥不对的合法形态 token 同样拒绝
        TokenService otherKey = service("another-secret");
        assertThrows(ApiException.class, () -> s.verify(otherKey.issue(5L)));
    }

    @Test
    void 非法输入_空串null_拒绝() {
        TokenService s = service("test-secret");
        assertThrows(ApiException.class, () -> s.verify(null));
        assertThrows(ApiException.class, () -> s.verify(""));
        assertThrows(ApiException.class, () -> s.verify("garbage"));
        assertThrows(ApiException.class, () -> s.verify("Bearer abc"));
    }
}
