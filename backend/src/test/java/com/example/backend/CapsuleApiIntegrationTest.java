package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.CapsuleDtos.CapsuleOpenRule;
import com.example.backend.dto.CapsuleDtos.CapsuleStatus;
import com.example.backend.dto.CapsuleDtos.CapsuleType;
import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.repository.TimeCapsuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 时光胶囊组集成测试,用例矩阵对齐 docs/design/v2.1-cards-design.md §3 用例 16-20:
 * 封存(ONE_YEAR_LATER 服务端算 365 天)/列表排序与待开数/详情门控/开箱幂等/越权 40400。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapsuleApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TimeCapsuleRepository capsuleRepository;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();
    private static final String RUN = Long.toHexString(System.nanoTime());

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private String login() {
        return post("/api/auth/login", null, Map.of("code",
                "dev-capsule-" + RUN + "-" + userCounter.incrementAndGet()))
                .get("token").asText();
    }

    private JsonNode get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private JsonNode post(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private JsonNode exchange(HttpMethod method, String path, String token, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        String response = body == null
                ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + response, e);
        }
    }

    private void assertErrorCode(JsonNode response, String code) {
        assertEquals(code, response.get("code").asText(), "响应: " + response);
    }

    private Map<String, Object> capsulePayload(String openRule, String openDate) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SELF");
        payload.put("openRule", openRule);
        if (openDate != null) {
            payload.put("openDate", openDate);
        }
        payload.put("coverHint", "一年不见，你还挺想我吧");
        payload.put("contentText", "封存给未来的信件内容。");
        return payload;
    }

    // ---- 用例 16:封存 ----

    @Test
    void 一年后规则服务端算365天_响应无内容字段() {
        JsonNode capsule = post("/api/capsules", login(), capsulePayload("ONE_YEAR_LATER", null));
        assertEquals(LocalDate.now().plusDays(365).toString(), capsule.get("openDate").asText());
        assertEquals(365, capsule.get("daysUntilOpen").asInt());
        assertEquals("SEALED", capsule.get("status").asText());
        assertFalse(capsule.has("contentText"), "SEALED 态不下发 contentText 键");
        assertFalse(capsule.has("photoUrl"), "SEALED 态不下发 photoUrl 键");
        assertTrue(capsule.has("sealedAt"));
        assertFalse(capsule.has("openedAt"), "SEALED 态 openedAt 不下发");
    }

    @Test
    void 预设规则传未来开箱日成功_过期或缺省拒绝() {
        String token = login();
        JsonNode capsule = post("/api/capsules", token,
                capsulePayload("GAOKAO_SCORE_DAY", LocalDate.now().plusDays(30).toString()));
        assertTrue(capsule.get("id").asLong() > 0);
        assertEquals(30, capsule.get("daysUntilOpen").asInt());
        assertFalse(capsule.get("dueNow").asBoolean());

        assertErrorCode(post("/api/capsules", token,
                capsulePayload("GAOKAO_SCORE_DAY", LocalDate.now().toString())), "40000");
        assertErrorCode(post("/api/capsules", token,
                capsulePayload("GAOKAO_SCORE_DAY", LocalDate.now().minusDays(1).toString())), "40000");
        assertErrorCode(post("/api/capsules", token, capsulePayload("GAOKAO_SCORE_DAY", null)), "40000");
        assertErrorCode(post("/api/capsules", token,
                capsulePayload("BAD_RULE", LocalDate.now().plusDays(30).toString())), "40000");
    }

    // ---- 用例 17:封存校验 ----

    @Test
    void 封面与内容含违禁词或超长均40000() {
        String token = login();
        Map<String, Object> forbiddenHint = capsulePayload("ONE_YEAR_LATER", null);
        forbiddenHint.put("coverHint", "大师给您写了句话");
        assertErrorCode(post("/api/capsules", token, forbiddenHint), "40000");
        Map<String, Object> forbiddenText = capsulePayload("ONE_YEAR_LATER", null);
        forbiddenText.put("contentText", "这是你的运势，注定好运。");
        assertErrorCode(post("/api/capsules", token, forbiddenText), "40000");
        Map<String, Object> tooLong = capsulePayload("ONE_YEAR_LATER", null);
        tooLong.put("contentText", "字".repeat(501));
        assertErrorCode(post("/api/capsules", token, tooLong), "40000");
        Map<String, Object> emptyHint = capsulePayload("ONE_YEAR_LATER", null);
        emptyHint.put("coverHint", "");
        assertErrorCode(post("/api/capsules", token, emptyHint), "40000");
    }

    // ---- 用例 18:列表排序与待开数 ----

    @Test
    void 列表SEALED升序在前OPENED排后_dueCount统计() {
        String token = login();
        post("/api/capsules", token, capsulePayload("GAOKAO_SCORE_DAY",
                LocalDate.now().plusDays(10).toString()));
        post("/api/capsules", token, capsulePayload("GAOKAO_SCORE_DAY",
                LocalDate.now().plusDays(5).toString()));
        // 直写构造:SEALED 且 openDate=今天(到期待开)
        TimeCapsuleEntity due = newCapsule(token);
        due.setOpenDate(LocalDate.now());
        capsuleRepository.saveAndFlush(due);
        // 直写构造:已开箱
        TimeCapsuleEntity opened = newCapsule(token);
        opened.setStatus(CapsuleStatus.OPENED);
        opened.setOpenedAt(LocalDateTime.now().minusHours(1));
        capsuleRepository.saveAndFlush(opened);

        JsonNode list = get("/api/capsules", token);
        assertEquals(4, list.get("items").size());
        assertEquals(1, list.get("dueCount").asInt(), "待开数=1");
        JsonNode items = list.get("items");
        // SEALED 按 openDate 升序在前(今天→+5→+10),OPENED 排后
        assertEquals(0, items.get(0).get("daysUntilOpen").asInt());
        assertTrue(items.get(0).get("dueNow").asBoolean(), "到期未开即 dueNow");
        assertEquals(5, items.get(1).get("daysUntilOpen").asInt());
        assertEquals(10, items.get(2).get("daysUntilOpen").asInt());
        assertEquals("OPENED", items.get(3).get("status").asText());
        // SEALED 列表项不含内容字段
        assertFalse(items.get(1).has("contentText"));
        assertTrue(items.get(3).has("contentText"), "OPENED 列表项回看完整内容");
    }

    // ---- 用例 19:详情门控与开箱幂等 ----

    @Test
    void 详情SEALED只回封面_未到期开箱40900_到期开箱下发内容且幂等() {
        String token = login();
        long id = post("/api/capsules", token,
                capsulePayload("GAOKAO_SCORE_DAY", LocalDate.now().plusDays(3).toString()))
                .get("id").asLong();
        JsonNode detail = get("/api/capsules/" + id, token);
        assertEquals("SEALED", detail.get("status").asText());
        assertFalse(detail.has("contentText"), "SEALED 详情不下发内容字段");
        assertFalse(detail.has("photoUrl"));
        assertEquals(3, detail.get("daysUntilOpen").asInt());

        // 未到期 → 40900
        assertErrorCode(post("/api/capsules/" + id + "/open", token, null), "40900");

        // 构造到期(openDate=昨天,保持 SEALED)
        TimeCapsuleEntity capsule = capsuleRepository.findById(id).orElseThrow();
        capsule.setOpenDate(LocalDate.now().minusDays(1));
        capsule.setPhotoUrl("https://cdn.example.test/photo.png");
        capsuleRepository.saveAndFlush(capsule);

        JsonNode opened = post("/api/capsules/" + id + "/open", token, null);
        assertEquals("OPENED", opened.get("status").asText());
        assertEquals("封存给未来的信件内容。", opened.get("contentText").asText());
        assertEquals("https://cdn.example.test/photo.png", opened.get("photoUrl").asText());
        assertTrue(opened.get("openedAt").asText().length() > 0);
        assertEquals(0, opened.get("daysUntilOpen").asInt());

        // 幂等:重复 open 返回首次结果,openedAt 不变
        JsonNode reopen = post("/api/capsules/" + id + "/open", token, null);
        assertEquals(opened.get("openedAt").asText(), reopen.get("openedAt").asText());
        assertEquals(opened.get("contentText").asText(), reopen.get("contentText").asText());

        // 开箱后详情常开(内容字段下发)
        JsonNode after = get("/api/capsules/" + id, token);
        assertEquals("OPENED", after.get("status").asText());
        assertEquals("封存给未来的信件内容。", after.get("contentText").asText());
        assertFalse(after.get("dueNow").asBoolean());
    }

    // ---- 用例 20:越权 ----

    @Test
    void 他人胶囊详情与开箱均40400() {
        String token = login();
        String other = login();
        long otherId = post("/api/capsules", other, capsulePayload("ONE_YEAR_LATER", null))
                .get("id").asLong();
        assertErrorCode(get("/api/capsules/" + otherId, token), "40400");
        assertErrorCode(post("/api/capsules/" + otherId + "/open", token, null), "40400");
        assertErrorCode(get("/api/capsules/" + 99999999, token), "40400");

        // 未登录
        assertErrorCode(get("/api/capsules", null), "40100");
    }

    // ---- 私有工具 ----

    /** 直写一枚 SEALED 胶囊(openDate=今天,供到期/排序用例构造) */
    private TimeCapsuleEntity newCapsule(String token) {
        long userId = Long.parseLong(token);
        TimeCapsuleEntity capsule = new TimeCapsuleEntity();
        capsule.setUserId(userId);
        capsule.setType(CapsuleType.SELF);
        capsule.setOpenRule(CapsuleOpenRule.GAOKAO_SCORE_DAY);
        capsule.setOpenDate(LocalDate.now());
        capsule.setCoverHint("直写构造的封面提示");
        capsule.setContentText("直写构造的封存内容。");
        capsule.setStatus(CapsuleStatus.SEALED);
        capsule.setSealedAt(LocalDateTime.now());
        return capsule;
    }
}
