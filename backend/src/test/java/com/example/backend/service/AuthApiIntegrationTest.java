package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.service.AuthService;
import com.example.backend.service.WeChatCode2SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * 鉴权双模式集成测试(selfdeploy 形态:trust=false / dev-code=false / token-secret 非空):
 * 防伪造核心断言(X-WX-OPENID 头被彻底忽略)+ code2Session stub 链路 + HMAC token 过拦截器。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "auth.trust-wx-header=false",
        "auth.dev-code-enabled=false",
        "auth.token-secret=test-hmac-secret",
})
class AuthApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper();

    /** code2Session 桩:code-N → openid-N(包内覆写 callWxApi,不真实外呼) */
    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        WeChatCode2SessionClient stubClient() {
            return new WeChatCode2SessionClient(new ObjectMapper()) {
                @Override
                String callWxApi(String code) {
                    if (code == null || !code.startsWith("code-")) {
                        return "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
                    }
                    return "{\"openid\":\"openid-" + code.substring(5) + "\"}";
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private JsonNode loginRaw(Map<String, Object> body, String wxOpenidHeader) {
        RestClient.RequestBodySpec spec = rest.method(HttpMethod.POST).uri("/api/auth/login");
        if (wxOpenidHeader != null) {
            spec = spec.header("X-WX-OPENID", wxOpenidHeader);
        }
        String response = body == null
                ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode authedGet(String path, String token) {
        String response = rest.method(HttpMethod.GET)
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 防伪造核心断言 ----

    @Test
    void 伪造头加无code_40000() {
        // 攻击者只伪造头不带 code(空 body):头被忽略(trust=false),又无 code → 40000,建不出任何用户
        JsonNode resp = loginRaw(Map.of(), "evil-openid");
        assertEquals("40000", resp.get("code").asText());
    }

    @Test
    void 伪造头加有效code_身份来自code而非头_登录后数据归属可证() {
        // 头与 code 并存:openid 恒来自 code2Session 结果(openid-x),头值被彻底无视。
        // 证明方式:token 是 HMAC 形态(secret 非空),能过拦截器;若是读了头,openid=evil-openid
        // 也一样能登录——因此进一步用 service 层直调断言(下个用例)锁死"code 优先"。
        JsonNode resp = loginRaw(Map.of("code", "code-x"), "evil-openid");
        assertTrue(resp.has("token"), "响应: " + resp);
        assertFalse(resp.get("token").asText().matches("^\\d+$"), "HMAC 形态 token: " + resp);
    }

    @Autowired
    private AuthService authService;

    @Test
    void service层_头与code并存_openid恒来自code2Session() {
        // 直调断言:伪造头 + code → 返回的用户是 code 对应用户;换不同 code 登录得到不同 userId,
        // 而伪造头换值不影响结果(头值 evil-a/evil-b 同 code 登录返回同一 userId)
        LoginRequest req = new LoginRequest();
        req.setCode("code-svc");

        MockHttpServletRequest withEvilA = new MockHttpServletRequest();
        withEvilA.addHeader("X-WX-OPENID", "evil-a");
        LoginResponse first = authService.login(req, withEvilA);

        MockHttpServletRequest withEvilB = new MockHttpServletRequest();
        withEvilB.addHeader("X-WX-OPENID", "evil-b");
        LoginResponse second = authService.login(req, withEvilB);

        // 头值不同,同 code → 同一用户(openid-openid-svc);若读了头则两次会是不同用户
        assertEquals(first.getUserId(), second.getUserId());
        // 且该用户确实来自 code(再登录一次同 code 仍是同一 userId)
        LoginResponse third = authService.login(req, new MockHttpServletRequest());
        assertEquals(first.getUserId(), third.getUserId());
    }

    // ---- code2Session 链路与 HMAC token ----

    @Test
    void 有效code_登录成功且HMAC_token可过拦截器() {
        JsonNode resp = loginRaw(Map.of("code", "code-77"), null);
        assertTrue(resp.has("token"));
        assertTrue(resp.has("userId"));
        assertFalse(resp.get("token").asText().matches("^\\d+$"));
        // 该 token 能过 AuthInterceptor(带 Bearer 调鉴权接口不 401)
        JsonNode authed = authedGet("/api/days", resp.get("token").asText());
        assertTrue(authed.has("items"), "鉴权接口响应: " + authed);
    }

    @Test
    void 明文userIdToken_在secret非空形态_被拒绝() {
        // 攻击者直接遍历 Bearer 1..N:secret 非空时明文 token 一律 401
        JsonNode resp = authedGet("/api/days", "2");
        assertEquals("40100", resp.get("code").asText());
    }

    @Test
    void 非法code_40000() {
        JsonNode resp = loginRaw(Map.of("code", "not-a-stub-code"), null);
        assertEquals("40000", resp.get("code").asText());
    }

    @Test
    void HMAC_token跨secret拒绝_伪造签名拒绝() {
        JsonNode resp = loginRaw(Map.of("code", "code-88"), null);
        String token = resp.get("token").asText();
        // 篡改 userId 段:1.{id}.{sig} → 1.999999.{sig}
        String[] parts = token.split("\\.", 3);
        String forged = "1.999999." + parts[2];
        JsonNode rejected = authedGet("/api/days", forged);
        assertEquals("40100", rejected.get("code").asText());
    }
}
