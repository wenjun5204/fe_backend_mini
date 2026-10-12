package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.DayDtos.DayKind;
import com.example.backend.entity.DayRecordEntity;
import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.entity.VisitorUnlockEntity;
import com.example.backend.repository.TimeCapsuleRepository;
import com.example.backend.repository.VisitorUnlockRepository;
import com.example.backend.service.DayTextLib;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 模板馆卡片组集成测试(H2,登录走真实链路),用例矩阵对齐 docs/design/v2.1-cards-design.md §3 用例 1-15。
 * 用户 code 每次运行带唯一前缀:感恩信每日 5 次频控按 userId+日期落库,防止跨运行污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CardsApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TimeCapsuleRepository capsuleRepository;
    @Autowired
    private VisitorUnlockRepository visitorUnlockRepository;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();
    /** 每次测试运行唯一前缀,避免与历史运行的频控账本/用户冲突 */
    private static final String RUN = Long.toHexString(System.nanoTime());

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("code", "dev-cards-" + RUN + "-" + userCounter.incrementAndGet()))
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

    /** 原始响应体(40400 不泄露存在性断言需逐字节比较) */
    private String raw(HttpMethod method, String path, String token, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        return body == null
                ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
    }

    private JsonNode exchange(HttpMethod method, String path, String token, Map<String, Object> body) {
        return parse(raw(method, path, token, body));
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

    private Map<String, Object> letterPayload(String... paragraphs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipient", "妈妈");
        payload.put("tone", "TEAR");
        payload.put("paragraphs", List.of(paragraphs));
        payload.put("signText", "—— 你的孩子");
        payload.put("styleKey", "SUNSET");
        return payload;
    }

    private Map<String, Object> cardRequest(String kind, Map<String, Object> payload) {
        Map<String, Object> request = new HashMap<>();
        request.put("templateKind", kind);
        request.put("payload", payload);
        return request;
    }

    private Map<String, Object> generateRequest(String recipient, List<String> stories, String tone) {
        Map<String, Object> request = new HashMap<>();
        request.put("recipient", recipient);
        request.put("stories", stories);
        request.put("tone", tone);
        return request;
    }

    private static final List<String> THREE_STORIES = List.of(
            "她总给我留饭", "送我上学那些年", "她爱唠叨但都是好意");

    // ---- 用例 1-2:GET /api/templates ----

    @Test
    void 模板全量6条按sort升序且含预约态() {
        JsonNode items = get("/api/templates", login()).get("items");
        assertEquals(6, items.size());
        int previousSort = 0;
        int notLive = 0;
        for (JsonNode item : items) {
            assertTrue(item.get("sort").asInt() >= previousSort, "按 sort 升序");
            previousSort = item.get("sort").asInt();
            if (!item.get("live").asBoolean()) {
                notLive++;
                assertNull(item.path("makerPage").asText(null), "live=false 不应有制作页");
                assertFalse(item.get("previewMeta").get("feats").isEmpty());
            }
        }
        assertEquals(2, notLive, "两条预约态模板在列");
    }

    @Test
    void 分类过滤FAMILY只剩两条() {
        JsonNode items = get("/api/templates?category=FAMILY", login()).get("items");
        assertEquals(2, items.size());
        for (JsonNode item : items) {
            assertEquals("FAMILY", item.get("category").asText());
        }
    }

    @Test
    void previewMeta全字段非空且过违禁词表() {
        JsonNode items = get("/api/templates", login()).get("items");
        for (JsonNode item : items) {
            JsonNode meta = item.get("previewMeta");
            for (String field : List.of("kicker", "emoji", "sampleText", "backgroundKey", "sub",
                    "usedLabel", "durationLabel", "freeLabel", "hint")) {
                assertTrue(meta.get(field).asText().length() > 0, field + " 非空");
            }
            assertEquals("✅ 免费", meta.get("freeLabel").asText(), "freeLabel 恒「✅ 免费」");
            for (String word : DayTextLib.FORBIDDEN_WORDS) {
                assertFalse(serialize(meta).contains(word),
                        "previewMeta 含违禁词「" + word + "」: " + item.get("key").asText());
            }
        }
    }

    @Test
    void 未登录访问模板清单被拒() {
        assertErrorCode(get("/api/templates", null), "40100");
    }

    // ---- 用例 3-6:POST /api/templates/letter/generate ----

    @Test
    void 无Key环境主路径FALLBACK_段数与结构合规() {
        JsonNode response = post("/api/templates/letter/generate", login(),
                generateRequest("妈妈", THREE_STORIES, "TEAR"));
        assertEquals("FALLBACK", response.get("source").asText());
        assertEquals(2 + THREE_STORIES.size(), response.get("paragraphs").size());
        assertTrue(response.get("generationId").asText().length() > 0);
        assertEquals("—— 你的孩子", response.get("signText").asText());
        JsonNode paragraphs = response.get("paragraphs");
        assertEquals("OPENING", paragraphs.get(0).get("role").asText());
        assertEquals("CLOSING", paragraphs.get(paragraphs.size() - 1).get("role").asText());
        for (int i = 0; i < paragraphs.size(); i++) {
            JsonNode segment = paragraphs.get(i);
            assertEquals(i, segment.get("index").asInt());
            assertTrue(segment.get("variants").size() >= 2, "每段 ≥2 写法");
            assertTrue(serialize(segment.get("variants")).contains(segment.get("selected").asText()),
                    "selected ∈ variants");
            for (String word : DayTextLib.FORBIDDEN_WORDS) {
                assertFalse(serialize(segment.get("variants")).contains(word), "AI 输出过词表");
            }
            if (segment.get("role").asText().equals("STORY")) {
                assertEquals(THREE_STORIES.get(i - 1), segment.get("storyKey").asText(), "故事段保序");
            }
        }
    }

    @Test
    void 同参两次调用段文案可复现() {
        String token = login();
        Map<String, Object> request = generateRequest("妈妈", THREE_STORIES, "FUN");
        JsonNode first = post("/api/templates/letter/generate", token, request);
        JsonNode second = post("/api/templates/letter/generate", token, request);
        assertEquals(first.get("paragraphs").toString(), second.get("paragraphs").toString(),
                "纯静态组装,同参可复现");
    }

    @Test
    void 生成参数校验_故事与语气非法返回40000() {
        String token = login();
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("妈妈", List.of(), "TEAR")), "40000");
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("妈妈", List.of("不存在的素材"), "TEAR")), "40000");
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("妈妈", List.of("她总给我留饭", "她总给我留饭"), "TEAR")), "40000");
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("妈妈", THREE_STORIES, "ANGRY")), "40000");
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("", THREE_STORIES, "TEAR")), "40000");
    }

    @Test
    void 频控每日5次_第6次40300_40000不耗额度() {
        String token = login();
        // 先来一次非法调用(不耗额度)
        assertErrorCode(post("/api/templates/letter/generate", token,
                generateRequest("妈妈", List.of(), "TEAR")), "40000");
        Map<String, Object> request = generateRequest("妈妈", THREE_STORIES, "CALM");
        for (int i = 0; i < 5; i++) {
            assertEquals("FALLBACK", post("/api/templates/letter/generate", token, request)
                    .get("source").asText());
        }
        // 第 6 次(AI 与降级统一计数,本环境全 FALLBACK 也计满)→ 40300
        assertErrorCode(post("/api/templates/letter/generate", token, request), "40300");
    }

    // ---- 用例 8-11:POST /api/cards ----

    @Test
    void LETTER定稿主路径_styleKey强制NIGHT_落款服务端常量() {
        JsonNode card = post("/api/cards", login(),
                cardRequest("LETTER", letterPayload("妈，见字如面。", "这些年您总给我留饭。",
                        "妈，往后换我来惦记您。")));
        assertTrue(card.get("id").asLong() > 0);
        assertEquals("LETTER", card.get("templateKind").asText());
        assertEquals("NIGHT", card.get("payload").get("styleKey").asText(), "忽略请求值强制 NIGHT");
        assertEquals("—— 你的孩子", card.get("payload").get("signText").asText());
        assertEquals(3, card.get("payload").get("paragraphs").size());
        assertEquals("ACTIVE", card.get("status").asText());
        assertTrue(card.get("shareScene").isNull(), "存档时未签发");
        assertEquals(0, card.get("totalHearts").asInt());
    }

    @Test
    void LETTER校验矩阵_段数超界超长违禁词伪落款均40000() {
        String token = login();
        // 段数超 9
        assertErrorCode(post("/api/cards", token, cardRequest("LETTER",
                letterPayload("一", "二", "三", "四", "五", "六", "七", "八", "九", "十"))), "40000");
        // 单段 121 字
        assertErrorCode(post("/api/cards", token, cardRequest("LETTER",
                letterPayload("字".repeat(121)))), "40000");
        // 含违禁词
        assertErrorCode(post("/api/cards", token, cardRequest("LETTER",
                letterPayload("您是大大师。"))), "40000");
        // 伪造落款
        Map<String, Object> payload = letterPayload("妈，见字如面。");
        payload.put("signText", "—— 伪造落款");
        assertErrorCode(post("/api/cards", token, cardRequest("LETTER", payload)), "40000");
        // templateKind 非法(预约态模板不可制作)
        assertErrorCode(post("/api/cards", token, cardRequest("HARD_FOR_DAD",
                letterPayload("妈，见字如面。"))), "40000");
        // LETTER 带 capsuleId → 40000
        Map<String, Object> withCapsule = cardRequest("LETTER", letterPayload("妈，见字如面。"));
        withCapsule.put("capsuleId", 1);
        assertErrorCode(post("/api/cards", token, withCapsule), "40000");
    }

    @Test
    void DAYS_CARD独立起始日成功_关联他人或未来日均拒绝() {
        String token = login();
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老伴");
        payload.put("startDate", LocalDate.now().toString());
        payload.put("styleKey", "SUNSET");
        JsonNode card = post("/api/cards", token, cardRequest("DAYS_CARD", payload));
        assertEquals("DAYS_CARD", card.get("templateKind").asText());
        assertTrue(card.get("sourceDayId").isNull(), "独立输入起始日无关联");
        assertEquals(LocalDate.now().toString(), card.get("payload").get("startDate").asText());

        // 未来起始日 → 40000
        Map<String, Object> future = new HashMap<>(payload);
        future.put("startDate", LocalDate.now().plusDays(1).toString());
        assertErrorCode(post("/api/cards", token, cardRequest("DAYS_CARD", future)), "40000");
    }

    @Test
    void DAYS_CARD关联本人TOGETHER日子_起始日以日子为准() {
        String token = login();
        long dayId = createTogetherDay(token, "老伴");
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老伴");
        payload.put("startDate", LocalDate.now().minusDays(10).toString());
        payload.put("styleKey", "MINT");
        Map<String, Object> request = cardRequest("DAYS_CARD", payload);
        request.put("sourceDayId", dayId);
        JsonNode card = post("/api/cards", token, request);
        assertEquals(dayId, card.get("sourceDayId").asLong(), "sourceDayId 回显");
        // 服务端以关联记录起始日覆盖请求 startDate(单一权威):TOGETHER 缺省月日=创建当天
        assertEquals(LocalDate.now().toString(), card.get("payload").get("startDate").asText());
    }

    @Test
    void DAYS_CARD关联越权或不存在日子_40000_40400() {
        String token = login();
        String other = login();
        long otherDayId = createTogetherDay(other, "老伴");
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老伴");
        payload.put("startDate", LocalDate.now().toString());
        payload.put("styleKey", "NIGHT");
        Map<String, Object> request = cardRequest("DAYS_CARD", payload);
        request.put("sourceDayId", otherDayId);
        assertErrorCode(post("/api/cards", token, request), "40000");
        Map<String, Object> missing = cardRequest("DAYS_CARD", payload);
        missing.put("sourceDayId", 99999999);
        assertErrorCode(post("/api/cards", token, missing), "40400");
    }

    @Test
    void CAPSULE卡片_胶囊字段以实体为权威_他人或非SEALED均40400() {
        String token = login();
        long capsuleId = sealCapsule(token, "一年不见，你还挺想我吧");
        Map<String, Object> payload = new HashMap<>();
        payload.put("coverHint", "请求方封面提示");
        payload.put("openDate", LocalDate.now().plusDays(1).toString());
        payload.put("type", "SELF");
        Map<String, Object> request = cardRequest("CAPSULE", payload);
        request.put("capsuleId", capsuleId);
        JsonNode card = post("/api/cards", token, request);
        assertEquals(capsuleId, card.get("capsuleId").asLong());
        assertEquals("一年不见，你还挺想我吧", card.get("payload").get("coverHint").asText(),
                "coverHint 以胶囊实体为权威覆盖");
        assertEquals("MINT", card.get("payload").get("styleKey").asText());
    }

    @Test
    void CAPSULE卡片_越权非SEALED不存在均40400() {
        String token = login();
        String other = login();
        long otherCapsule = sealCapsule(other, "他人的胶囊");
        Map<String, Object> request = capsuleCardRequest(otherCapsule);
        assertErrorCode(post("/api/cards", token, request), "40400");
        Map<String, Object> missing = capsuleCardRequest(99999999);
        assertErrorCode(post("/api/cards", token, missing), "40400");
        // 已开箱(非 SEALED)胶囊 → 40400
        long capsuleId = sealCapsule(token, "已开的胶囊");
        TimeCapsuleEntity capsule = capsuleRepository.findById(capsuleId).orElseThrow();
        capsule.setStatus(com.example.backend.dto.CapsuleDtos.CapsuleStatus.OPENED);
        capsule.setOpenedAt(LocalDateTime.now());
        capsuleRepository.saveAndFlush(capsule);
        assertErrorCode(post("/api/cards", token, capsuleCardRequest(capsuleId)), "40400");
    }

    // ---- 用例 12-15:GET/DELETE/share ----

    @Test
    void 三类卡片列表降序_统计聚合正确_空账号空列表() {
        assertEquals(0, get("/api/cards", login()).get("items").size());

        String token = login();
        long letter = post("/api/cards", token, cardRequest("LETTER",
                letterPayload("一", "二", "三"))).get("id").asLong();
        long days = post("/api/cards", token,
                cardRequest("DAYS_CARD", daysPayload())).get("id").asLong();
        long capsuleCard = post("/api/cards", token,
                capsuleCardRequest(sealCapsule(token, "封面"))).get("id").asLong();

        // 访客点心(直接落 visitor_unlock):letter 卡 +2,days 卡 +3
        insertHeart(letter, "a".repeat(32), 2);
        insertHeart(days, "b".repeat(32), 3);

        JsonNode list = get("/api/cards", token);
        assertEquals(3, list.get("total").asInt());
        assertEquals(5, list.get("heartsReceived").asInt(), "收到的心=各卡合计");
        JsonNode items = list.get("items");
        assertEquals(3, items.size());
        assertEquals(capsuleCard, items.get(0).get("id").asLong(), "createdAt 降序");
        assertEquals(days, items.get(1).get("id").asLong());
        assertEquals(letter, items.get(2).get("id").asLong());
        assertEquals(2, items.get(2).get("totalHearts").asInt());
        assertEquals(3, items.get(1).get("totalHearts").asInt());
        assertEquals(0, items.get(0).get("totalHearts").asInt());
    }

    @Test
    void 详情返回完整payload_越权与不存在响应逐字节一致() {
        String token = login();
        String other = login();
        long otherCard = post("/api/cards", other,
                cardRequest("LETTER", letterPayload("一", "二", "三"))).get("id").asLong();

        JsonNode mine = post("/api/cards", token, cardRequest("LETTER",
                letterPayload("妈，见字如面。", "这些年您总给我留饭。", "妈，往后换我来惦记您。")));
        JsonNode detail = get("/api/cards/" + mine.get("id").asLong(), token);
        assertEquals(mine.get("payload"), detail.get("payload"), "详情返回完整 payload");

        String otherResponse = raw(HttpMethod.GET, "/api/cards/" + otherCard, token, null);
        String missingResponse = raw(HttpMethod.GET, "/api/cards/" + 99999999, token, null);
        assertEquals(missingResponse, otherResponse, "越权与不存在 40400 响应逐字节一致");
    }

    @Test
    void 删除软删幂等_删后列表与详情不可见() {
        String token = login();
        long id = post("/api/cards", token, cardRequest("LETTER",
                letterPayload("一", "二", "三"))).get("id").asLong();
        assertTrue(get("/api/cards/" + id, token).get("id").asLong() == id);
        assertTrue(delete("/api/cards/" + id, token).get("deleted").asBoolean());
        assertTrue(delete("/api/cards/" + id, token).get("deleted").asBoolean(), "重复删幂等 true");
        assertErrorCode(get("/api/cards/" + id, token), "40400");
        assertEquals(0, get("/api/cards", token).get("total").asInt(), "删后列表消失");
    }

    @Test
    void share幂等同码_8位base62_越权与已删40400() {
        String token = login();
        String other = login();
        long id = post("/api/cards", token, cardRequest("LETTER",
                letterPayload("一", "二", "三"))).get("id").asLong();
        JsonNode first = post("/api/cards/" + id + "/share", token, null);
        JsonNode second = post("/api/cards/" + id + "/share", token, null);
        assertEquals(first.get("scene").asText(), second.get("scene").asText(), "同卡片恒同码");
        assertTrue(Pattern.matches("[0-9a-zA-Z]{8}", first.get("scene").asText()));
        assertEquals("pages/visitor/visitor?scene=" + first.get("scene").asText(),
                first.get("pagePath").asText());

        // 他人卡片 → 40400
        long otherCard = post("/api/cards", other, cardRequest("LETTER",
                letterPayload("一", "二", "三"))).get("id").asLong();
        assertErrorCode(post("/api/cards/" + otherCard + "/share", token, null), "40400");
        // 删除后的卡片 → 40400
        delete("/api/cards/" + id, token);
        assertErrorCode(post("/api/cards/" + id + "/share", token, null), "40400");
    }

    // ---- BIRTHDAY_CARD 组(v1.8.0,对齐 v2.2-family-design.md §3 用例 24-26) ----

    @Test
    void 生日福卡主路径_relation以日子服务端覆盖_存档回显_卡片墙可见() {
        String owner = family()[0];
        long dayId = createBirthdayDay(owner, "妈妈");
        long momentId = publishMoment(owner, "妈妈在院子里晒被子");
        collectMoment(owner, momentId, dayId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("dayId", dayId);
        payload.put("relation", "我想传一个会被覆盖的称呼");
        payload.put("blessingText", "祝妈妈生日快乐，天天都有好心情。");
        payload.put("momentIds", List.of(momentId));
        payload.put("styleKey", "WARM");
        JsonNode card = post("/api/cards", owner, cardRequest("BIRTHDAY_CARD", payload));
        assertEquals("BIRTHDAY_CARD", card.get("templateKind").asText());
        JsonNode archived = card.get("payload");
        assertEquals(dayId, archived.get("dayId").asLong());
        assertEquals("妈妈", archived.get("relation").asText(), "relation 服务端以 DayRecord 覆盖(单一权威)");
        assertEquals("祝妈妈生日快乐，天天都有好心情。", archived.get("blessingText").asText());
        assertEquals(momentId, archived.get("momentIds").get(0).asLong());
        assertEquals("WARM", archived.get("styleKey").asText());

        // 卡片墙可见
        JsonNode wall = get("/api/cards", owner);
        assertEquals(1, wall.get("total").asInt());
        assertEquals("BIRTHDAY_CARD", wall.get("items").get(0).get("templateKind").asText());
        // 详情回显同 payload
        JsonNode detail = get("/api/cards/" + card.get("id").asLong(), owner);
        assertEquals(dayId, detail.get("payload").get("dayId").asLong());
    }

    /** 裁定修订回归:生日福卡随时可生成,无「当天 daysUntil=0」门控(提前/过后均 200) */
    @Test
    void 非生日当天生成福卡200_提前与过后均无40900门控() {
        String owner = family()[0];
        // 落点提前 1 天的生日日子
        long aheadDay = createBirthdayDayAt(owner, "爸爸", LocalDate.now().plusDays(1));
        long momentId = publishMoment(owner, "提前做的福卡");
        collectMoment(owner, momentId, aheadDay);
        JsonNode ahead = post("/api/cards", owner, birthdayCardRequest(aheadDay, "提前一天也 200",
                List.of(momentId), "MINT"));
        assertEquals("BIRTHDAY_CARD", ahead.get("templateKind").asText(), "非当天生成无 409 门控");

        // 落点已过(昨天)的生日日子同样可生成
        long pastDay = createBirthdayDayAt(owner, "老伴", LocalDate.now().minusDays(1));
        long pastMoment = publishMoment(owner, "过后补做也 200");
        collectMoment(owner, pastMoment, pastDay);
        JsonNode past = post("/api/cards", owner, birthdayCardRequest(pastDay, "生日过后补做",
                List.of(pastMoment), "WARM"));
        assertEquals("BIRTHDAY_CARD", past.get("templateKind").asText());
    }

    @Test
    void 生日福卡校验矩阵_素材池外瞬间与越界日子与foreign字段均40000() {
        String owner = family()[0];
        long dayId = createBirthdayDay(owner, "妈妈");
        long inPoolMoment = publishMoment(owner, "已在素材池");
        collectMoment(owner, inPoolMoment, dayId);
        long outPoolMoment = publishMoment(owner, "不在素材池的瞬间");
        long anniversaryDay = createDay(owner, "ANNIVERSARY", "FAMILY");

        // momentIds 含非该日子素材池瞬间 → 40000
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "素材校验", List.of(outPoolMoment), "WARM")), "40000");
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "素材校验", List.of(inPoolMoment, outPoolMoment), "MINT")), "40000");
        // blessingText 41 字 / 违禁词 → 40000
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "福".repeat(41), List.of(inPoolMoment), "WARM")), "40000");
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "祝你运势亨通", List.of(inPoolMoment), "WARM")), "40000");
        // dayId 指向 ANNIVERSARY 日子 → 40000;dayId 他人 PRIVATE 不可见 → 40000(统一 400 口径)
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(anniversaryDay, "日子类型不对", List.of(), "WARM")), "40000");
        long strangerDay = createDay(login(), "BIRTHDAY", "PRIVATE");
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(strangerDay, "别人的日子", List.of(), "WARM")), "40000");
        // dayId 缺失 → 40000;styleKey=SUNSET → 40000;momentIds 空 → 40000
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(null, "没选日子", List.of(inPoolMoment), "WARM")), "40000");
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "样式不合法", List.of(inPoolMoment), "SUNSET")), "40000");
        assertErrorCode(post("/api/cards", owner,
                birthdayCardRequest(dayId, "一张照片都不选", List.of(), "WARM")), "40000");
        // LETTER 带 dayId → 40000(foreign field)
        Map<String, Object> letter = letterPayload("妈，见字如面。", "这些年您总给我留饭。");
        letter.put("dayId", dayId);
        assertErrorCode(post("/api/cards", owner, cardRequest("LETTER", letter)), "40000");
        // BIRTHDAY_CARD 带不适用字段 → 40000
        Map<String, Object> foreign = birthdayCardRequest(dayId, "带不适用字段", List.of(inPoolMoment), "MINT");
        foreign.put("signText", "—— 你的孩子");
        assertErrorCode(post("/api/cards", owner, cardRequest("BIRTHDAY_CARD", foreign)), "40000");
        // 素材瞬间被删后 payload 不追溯(已存档卡仍回显,生成时才校验)
        long deletedPoolDay = createBirthdayDay(owner, "外婆");
        long doomedMoment = publishMoment(owner, "将被删除的素材");
        collectMoment(owner, doomedMoment, deletedPoolDay);
        JsonNode card = post("/api/cards", owner,
                birthdayCardRequest(deletedPoolDay, "存档不追溯", List.of(doomedMoment), "WARM"));
        delete("/api/moments/" + doomedMoment, owner);
        JsonNode detail = get("/api/cards/" + card.get("id").asLong(), owner);
        assertEquals(doomedMoment, detail.get("payload").get("momentIds").get(0).asLong(),
                "已存档 payload 不随素材删除追溯");
    }

    // ---- 私有工具(BIRTHDAY_CARD 组) ----

    /** 独立用户建一个家:返回 [户主token, 成员token] */
    private String[] family() {
        String owner = login();
        String inviteCode = post("/api/family", owner, Map.of("name", "念家时")).get("inviteCode").asText();
        String member = login();
        post("/api/family/join", member, Map.of("inviteCode", inviteCode));
        return new String[] {owner, member};
    }

    private byte[] jpegBytes() {
        byte[] data = new byte[8];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private String uploadPhoto(String token) {
        return post("/api/photos", token, Map.of(
                "imageBase64", java.util.Base64.getEncoder().encodeToString(jpegBytes()),
                "scene", "MOMENT")).get("photoId").asText();
    }

    private long publishMoment(String token, String text) {
        return post("/api/moments", token,
                Map.of("photoId", uploadPhoto(token), "text", text)).get("id").asLong();
    }

    private void collectMoment(String token, long momentId, long dayId) {
        post("/api/moments/" + momentId + "/collect", token, Map.of("dayId", dayId));
    }

    private long createBirthdayDay(String token, String relation) {
        return createBirthdayDayAt(token, relation, LocalDate.now().plusDays(30));
    }

    private long createBirthdayDayAt(String token, String relation, LocalDate monthDay) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", relation);
        payload.put("kind", "BIRTHDAY");
        payload.put("calendarType", "SOLAR");
        payload.put("month", monthDay.getMonthValue());
        payload.put("day", monthDay.getDayOfMonth());
        payload.put("visibility", "PRIVATE");
        return post("/api/days", token, payload).get("id").asLong();
    }

    private Map<String, Object> birthdayCardRequest(Long dayId, String blessingText,
                                                    List<Long> momentIds, String styleKey) {
        Map<String, Object> payload = new HashMap<>();
        if (dayId != null) {
            payload.put("dayId", dayId);
        }
        payload.put("relation", "妈妈");
        payload.put("blessingText", blessingText);
        payload.put("momentIds", momentIds);
        payload.put("styleKey", styleKey);
        return cardRequest("BIRTHDAY_CARD", payload);
    }

    private long createDay(String token, String kind, String visibility) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老战友");
        payload.put("kind", kind);
        payload.put("calendarType", "SOLAR");
        payload.put("month", 7);
        payload.put("day", 1);
        payload.put("visibility", visibility);
        return post("/api/days", token, payload).get("id").asLong();
    }

    // ---- 私有工具 ----

    private long createTogetherDay(String token, String relation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", relation);
        payload.put("kind", "TOGETHER");
        payload.put("calendarType", "SOLAR");
        payload.put("visibility", "PRIVATE");
        return post("/api/days", token, payload).get("id").asLong();
    }

    private long sealCapsule(String token, String coverHint) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SELF");
        payload.put("openRule", "ONE_YEAR_LATER");
        payload.put("coverHint", coverHint);
        payload.put("contentText", "封存给未来的信件内容。");
        return post("/api/capsules", token, payload).get("id").asLong();
    }

    private Map<String, Object> daysPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老伴");
        payload.put("startDate", LocalDate.now().toString());
        payload.put("styleKey", "NIGHT");
        return payload;
    }

    private Map<String, Object> capsuleCardRequest(long capsuleId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("coverHint", "封面");
        payload.put("openDate", LocalDate.now().plusDays(1).toString());
        payload.put("type", "SELF");
        Map<String, Object> request = cardRequest("CAPSULE", payload);
        request.put("capsuleId", capsuleId);
        return request;
    }

    private void insertHeart(long cardId, String deviceId, int hearts) {
        VisitorUnlockEntity unlock = new VisitorUnlockEntity();
        unlock.setCardId(cardId);
        unlock.setDeviceId(deviceId);
        unlock.setHearts(hearts);
        unlock.setCreatedAt(LocalDateTime.now());
        unlock.setUpdatedAt(LocalDateTime.now());
        visitorUnlockRepository.saveAndFlush(unlock);
    }

    private String serialize(JsonNode node) {
        return node == null ? "" : node.toString();
    }
}
