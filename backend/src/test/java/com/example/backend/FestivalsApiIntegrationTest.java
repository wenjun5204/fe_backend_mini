package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 节日/上线预约集成测试(H2 内存库):幂等登记、清单 join、due 窗口与预约标记。
 * 用例矩阵对齐 docs/design/v2.2-family-design.md §3 festivals 组;
 * festivalDate 为 2027 年静态配置,due 用 ?date= 自测参数定位窗口。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FestivalsApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();

    private static final String MOTHERS_DAY = "2027-05-09";
    private static final String DADS_DAY = "2027-06-20";
    private static final String QIXI = "2027-08-08";

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return post("/api/auth/login", null, Map.of("code", "dev-festival-" + userCounter.incrementAndGet()))
                .get("token").asText();
    }

    private JsonNode get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private JsonNode post(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private JsonNode delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null);
    }

    private JsonNode exchange(HttpMethod method, String path, String token, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        String response = body == null
                ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
        return parse(response);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private void assertErrorCode(JsonNode response, String code) {
        assertEquals(code, response.get("code").asText(), "响应: " + response);
    }

    private JsonNode reserve(String token, String templateKey) {
        return post("/api/festivals/reservations", token, Map.of("templateKey", templateKey));
    }

    private JsonNode due(String token, String date) {
        return get("/api/festivals/due?date=" + date, token);
    }

    // ---- POST /api/festivals/reservations ----

    @Test
    void 预约幂等三连_重复登记返回首次时间() throws Exception {
        String token = login();
        JsonNode first = reserve(token, "HARD_FOR_DAD");
        assertEquals("HARD_FOR_DAD", first.get("templateKey").asText());
        assertTrue(first.get("reserved").asBoolean());
        assertFalse(first.get("reservedAt").asText().isBlank());

        Thread.sleep(10); // 保证时间戳可区分,验证幂等不更新登记时间
        JsonNode second = reserve(token, "HARD_FOR_DAD");
        assertEquals(first.get("reservedAt").asText(), second.get("reservedAt").asText(), "重复预约返回同条目");
        JsonNode third = reserve(token, "HARD_FOR_DAD");
        assertEquals(first.get("reservedAt").asText(), third.get("reservedAt").asText());
    }

    @Test
    void templateKey乱串40400_缺失40000() {
        String token = login();
        assertErrorCode(reserve(token, "FOO"), "40400");
        assertErrorCode(reserve(token, "hard_for_dad"), "40400", "枚举值域大小写敏感");
        assertErrorCode(post("/api/festivals/reservations", token, Map.of()), "40000");
    }

    private void assertErrorCode(JsonNode response, String code, String message) {
        assertEquals(code, response.get("code").asText(), message + ",响应: " + response);
    }

    // ---- GET /api/festivals/reservations ----

    @Test
    void 清单join模板字段且按登记时间降序() throws Exception {
        String token = login();
        reserve(token, "LETTER");
        Thread.sleep(10);
        reserve(token, "HARD_FOR_DAD");

        JsonNode list = get("/api/festivals/reservations", token);
        assertEquals(2, list.get("total").asInt());
        assertEquals(2, list.get("items").size());
        JsonNode hardItem = list.get("items").get(0);
        assertEquals("HARD_FOR_DAD", hardItem.get("templateKey").asText());
        assertEquals("给爸爸的一句硬话", hardItem.get("name").asText());
        assertFalse(hardItem.get("live").asBoolean(), "预约态模板 live=false");
        assertEquals(DADS_DAY, hardItem.get("festivalDate").asText());
        assertEquals("父亲节 👔", hardItem.get("festivalLabel").asText());
        int expectedDays = (int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(DADS_DAY));
        assertEquals(expectedDays, hardItem.get("daysUntil").asInt(), "daysUntil 按 festivalDate 计算");

        JsonNode letterItem = list.get("items").get(1);
        assertEquals("LETTER", letterItem.get("templateKey").asText());
        assertEquals(MOTHERS_DAY, letterItem.get("festivalDate").asText());
        assertEquals("母亲节 🌷", letterItem.get("festivalLabel").asText());
        assertTrue(letterItem.get("live").asBoolean());
    }

    @Test
    void 空账号清单为空() {
        JsonNode list = get("/api/festivals/reservations", login());
        assertEquals(0, list.get("total").asInt());
        assertEquals(0, list.get("items").size());
    }

    // ---- DELETE /api/festivals/reservations/{templateKey} ----

    @Test
    void 取消幂等_未预约再取消也成功() {
        String token = login();
        assertEquals(true, delete("/api/festivals/reservations/HARD_FOR_DAD", token).get("deleted").asBoolean());
        reserve(token, "HARD_FOR_DAD");
        assertEquals(1, get("/api/festivals/reservations", token).get("total").asInt());
        assertEquals(true, delete("/api/festivals/reservations/HARD_FOR_DAD", token).get("deleted").asBoolean());
        assertEquals(true, delete("/api/festivals/reservations/HARD_FOR_DAD", token).get("deleted").asBoolean(),
                "重复取消幂等");
        assertEquals(0, get("/api/festivals/reservations", token).get("total").asInt());
        assertErrorCode(delete("/api/festivals/reservations/FOO", token), "40000");
    }

    // ---- GET /api/festivals/due ----

    @Test
    void due窗口0与7天命中_8天与过期不含() {
        String token = login();
        // 母亲节 2027-05-09:前 7 天(05-02)与前 0 天(05-09)在窗口内
        JsonNode at7 = due(token, "2027-05-02");
        assertEquals(1, at7.get("total").asInt(), "窗口内仅母亲节(父亲节/七夕都在 7 天外)");
        JsonNode item7 = at7.get("items").get(0);
        assertEquals("LETTER", item7.get("templateKey").asText());
        assertEquals(7, item7.get("daysUntil").asInt());
        assertEquals(MOTHERS_DAY, item7.get("festivalDate").asText());
        assertEquals("🌷", item7.get("emoji").asText(), "emoji 从 festivalLabel 提取");

        JsonNode at0 = due(token, "2027-05-09");
        assertEquals(1, at0.get("total").asInt());
        assertEquals(0, at0.get("items").get(0).get("daysUntil").asInt());

        // 8 天出窗;节日次日(过期)出窗
        assertEquals(0, due(token, "2027-05-01").get("total").asInt());
        assertEquals(0, due(token, "2027-05-10").get("total").asInt());
    }

    @Test
    void due七夕窗口命中_静态农历标注() {
        String token = login();
        JsonNode atQixi7 = due(token, "2027-08-01");
        assertEquals(1, atQixi7.get("total").asInt());
        JsonNode qixi = atQixi7.get("items").get(0);
        assertEquals("WEDDING_CARD", qixi.get("templateKey").asText());
        assertEquals(7, qixi.get("daysUntil").asInt());
        assertEquals(QIXI, qixi.get("festivalDate").asText());
        assertEquals("🌌", qixi.get("emoji").asText());
        assertFalse(qixi.get("live").asBoolean());
    }

    @Test
    void due预约标记与dueSoon_3天内点亮() {
        String token = login();
        reserve(token, "HARD_FOR_DAD");
        JsonNode at7 = due(token, "2027-06-13").get("items").get(0);
        assertEquals(7, at7.get("daysUntil").asInt());
        assertTrue(at7.get("reservedByMe").asBoolean());
        assertFalse(at7.get("dueSoon").asBoolean(), ">3 天不点亮");

        JsonNode at3 = due(token, "2027-06-17").get("items").get(0);
        assertEquals(3, at3.get("daysUntil").asInt());
        assertTrue(at3.get("reservedByMe").asBoolean());
        assertTrue(at3.get("dueSoon").asBoolean(), "已预约且 ≤3 天点亮");

        // 未预约用户:reservedByMe=false,dueSoon 恒 false
        JsonNode fresh = due(login(), "2027-06-17").get("items").get(0);
        assertFalse(fresh.get("reservedByMe").asBoolean());
        assertFalse(fresh.get("dueSoon").asBoolean());
    }

    @Test
    void due日期非法40000_未登录40100() {
        String token = login();
        assertErrorCode(due(token, "2027/05/02"), "40000");
        assertErrorCode(due(token, "abc"), "40000");
        assertErrorCode(get("/api/festivals/due", null), "40100");
    }

    @Test
    void 预约与到期端点未登录被拒() {
        assertErrorCode(post("/api/festivals/reservations", null,
                Map.of("templateKey", "HARD_FOR_DAD")), "40100");
        assertErrorCode(get("/api/festivals/reservations", null), "40100");
        assertErrorCode(delete("/api/festivals/reservations/HARD_FOR_DAD", null), "40100");
    }
}
