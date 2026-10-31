package com.example.backend.service;

import com.example.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * token 签发与校验(双形态兼容):
 * - secret 空(云托管存量/本地/测试):legacy 明文 token = String.valueOf(userId),行为与历史完全一致;
 * - secret 非空(自部署,AuthModeGuard 强制):HMAC-SHA256 签名 token "1.{userId}.{hex[:32]}",
 *   无状态无会话表,公网下不可伪造/不可遍历。
 * 密钥轮换 = 全员 token 失效,前端 401 自动重登兜底。
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /** HMAC 截断长度(32 hex = 128 bit,对离线暴力足够) */
    private static final int HMAC_HEX_LENGTH = 32;

    /** HMAC token 形态:1.{userId}.{32位hex} */
    private static final String HMAC_PATTERN = "^1\\.\\d+\\.[0-9a-f]{32}$";

    /** legacy 明文 token 形态:纯数字 userId */
    private static final String LEGACY_PATTERN = "^\\d+$";

    /** 签名载荷前缀(域隔离,防止密钥被其他用途的 HMAC 消息复用) */
    private static final String PAYLOAD_PREFIX = "auth-token:";

    @Value("${auth.token-secret:}")
    private String secret;

    private volatile boolean legacyWarned = false;

    /** 单测注入密钥(生产走环境变量→properties) */
    void setSecret(String secret) {
        this.secret = secret;
    }

    /** 签发:secret 空 → legacy 明文(一次性 WARN 提示升级);非空 → HMAC 签名 */
    public String issue(Long userId) {
        if (secret == null || secret.isBlank()) {
            if (!legacyWarned) {
                legacyWarned = true;
                log.warn("auth.token-secret 未配置,token 为明文 userId(演示态);公网部署前必须配置 AUTH_TOKEN_SECRET");
            }
            return String.valueOf(userId);
        }
        return "1." + userId + "." + hmacHex(userId).substring(0, HMAC_HEX_LENGTH);
    }

    /** 校验:返回 userId;非法/伪造/形态与 secret 配置不符 → 40100 */
    public Long verify(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized();
        }
        String value = token.trim();
        if (value.matches(HMAC_PATTERN)) {
            int firstDot = value.indexOf('.');
            int secondDot = value.indexOf('.', firstDot + 1);
            long userId = Long.parseLong(value.substring(firstDot + 1, secondDot));
            if (secret == null || secret.isBlank()
                    || !value.substring(secondDot + 1).equals(hmacHex(userId).substring(0, HMAC_HEX_LENGTH))) {
                throw ApiException.unauthorized();
            }
            return userId;
        }
        // legacy 纯数字:仅 secret 为空(演示态)时接受;配了 secret 还收到明文 token = 必须拒绝
        if (value.matches(LEGACY_PATTERN) && (secret == null || secret.isBlank())) {
            return Long.parseLong(value);
        }
        throw ApiException.unauthorized();
    }

    private String hmacHex(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((PAYLOAD_PREFIX + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // HmacSHA256 为 JDK 内置,init 异常只可能是密钥编码问题
            throw new IllegalStateException("token 签名失败", e);
        }
    }
}
