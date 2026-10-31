package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信 code2Session 客户端(自部署链路):wx.login 的 code 换 openid。
 * 模式对齐 AiLetterService:Java 21 内置 HttpClient,零第三方 SDK;发 HTTP 的方法包内可见可覆写(测试替身)。
 * 安全红线(见 conventions.md):wx.secret 与 session_key 绝不落日志;响应只取 openid。
 * code 5 分钟有效且单次使用——超时不盲目重试(code 可能已被消费),失败让用户重进小程序重新 login。
 */
@Component
public class WeChatCode2SessionClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatCode2SessionClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** 单例客户端:连接 2s/每请求 5s/不跟随重定向 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper mapper;

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    @Value("${wx.api-base-url:https://api.weixin.qq.com}")
    private String apiBaseUrl;

    public WeChatCode2SessionClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 单测注入配置(生产走环境变量→properties) */
    void setConfig(String appid, String secret, String apiBaseUrl) {
        this.appid = appid;
        this.secret = secret;
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * code 换 openid;微信侧失败统一 40000(用户可重进小程序重新 login)。
     * errcode 概要入日志便于排查配置错误(appid/secret 配错常见表现 40013/40125),绝不记 secret。
     */
    public String exchangeOpenid(String code) {
        try {
            JsonNode root = mapper.readTree(callWxApi(code));
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                // 日志只记 errcode/errmsg(不含 appid/secret/code),响应体可能含敏感字段不入日志
                log.warn("code2Session 失败: errcode={}, errmsg={}",
                        root.path("errcode").asInt(), root.path("errmsg").asText());
                throw ApiException.param("登录状态过期，请您重新进入小程序");
            }
            String openid = root.path("openid").asText("");
            if (openid.isBlank()) {
                log.warn("code2Session 响应缺少 openid: errcode={}", root.path("errcode").asInt());
                throw ApiException.param("登录状态过期，请您重新进入小程序");
            }
            return openid;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // 网络/超时/解析异常:不重试(code 可能已消费),统一用户文案
            log.warn("code2Session 调用异常: {}", e.getClass().getSimpleName());
            throw ApiException.param("登录状态过期，请您重新进入小程序");
        }
    }

    /** 发 jscode2session 请求;包内可见——单测覆写喂伪造 JSON(不真实外呼) */
    String callWxApi(String code) throws Exception {
        String url = trimUrl(apiBaseUrl) + "/sns/jscode2session"
                + "?appid=" + appid
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("微信接口返回 " + response.statusCode());
        }
        return response.body();
    }

    private static String trimUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
