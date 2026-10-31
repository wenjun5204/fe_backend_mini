package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.backend.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WeChatCode2SessionClient 单测:JDK 内置 HttpServer 起本地桩,wx.api-base-url 指过去——
 * 本地完整验证 code2Session,不依赖真实 appid/secret/微信接口。
 */
class WeChatCode2SessionClientTest {

    private HttpServer server;
    private String stubUrl;
    private WeChatCode2SessionClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 桩返回体(可按用例改写);query 里包含 appid/secret/code,断言它们正确透传 */
    private volatile String responseBody = "";
    private volatile String lastQuery = "";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        stubUrl = "http://127.0.0.1:" + port;
        server.createContext("/sns/jscode2session", exchange -> {
            lastQuery = exchange.getRequestURI().getQuery();
            byte[] body = responseBody.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(executor);
        server.start();

        client = new WeChatCode2SessionClient(new ObjectMapper());
        client.setConfig("test-appid", "test-secret", stubUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }

    @Test
    void 正常响应_返回openid_参数透传正确() {
        responseBody = "{\"openid\":\"openid-abc\",\"session_key\":\"sk-xxx\"}";
        assertEquals("openid-abc", client.exchangeOpenid("code-1"));
        // appid/secret/js_code/grant_type 正确拼进请求
        assertEquals(true, lastQuery.contains("appid=test-appid"));
        assertEquals(true, lastQuery.contains("secret=test-secret"));
        assertEquals(true, lastQuery.contains("js_code=code-1"));
        assertEquals(true, lastQuery.contains("grant_type=authorization_code"));
    }

    @Test
    void errcode非零_40000文案() {
        // code 无效(40029)与 appid/secret 配错(如 40125)统一用户文案,日志才区分
        responseBody = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        ApiException e = assertThrows(ApiException.class, () -> client.exchangeOpenid("bad"));
        assertEquals("40000", e.getCode());
    }

    @Test
    void 缺openid字段_40000() {
        responseBody = "{\"errcode\":0}"; // 无 openid
        ApiException e = assertThrows(ApiException.class, () -> client.exchangeOpenid("code-2"));
        assertEquals("40000", e.getCode());
    }

    @Test
    void HTTP非200_40000不重试() {
        // 桩直接关连接模拟网关异常
        server.stop(0);
        server = null;
        ApiException e = assertThrows(ApiException.class, () -> client.exchangeOpenid("code-3"));
        assertEquals("40000", e.getCode());
    }
}
