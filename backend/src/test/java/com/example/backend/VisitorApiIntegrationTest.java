package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.entity.VisitorUnlockEntity;
import com.example.backend.repository.TimeCapsuleRepository;
import com.example.backend.repository.VisitorUnlockRepository;
import com.example.backend.service.CardTextLib;
import com.example.backend.service.VisitorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
 * 免鉴权访客组集成测试(全程不带 token),用例矩阵对齐 docs/design/v2.1-cards-design.md §3 用例 21-27。
 * 42900 软频控口径:频控先查且全部调用计入窗口(>3 次/分钟);幂等「已满 3 恒 3/true」的断言在
 * resetHeartRateWindowForTests(模拟窗口过期)后进行——与频控语义互补,不冲突。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisitorApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TimeCapsuleRepository capsuleRepository;
    @Autowired
    private VisitorUnlockRepository visitorUnlockRepository;
    @Autowired
    private VisitorService visitorService;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();
    private static final String RUN = Long.toHexString(System.nanoTime());

    private static final List<String> FOUR_PARAGRAPHS = List.of(
            "妈，见字如面。", "这些年您总给我留饭。", "送我上学那些年，您比闹钟还准时。",
            "妈，往后换我来惦记您。");

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
        visitorService.resetHeartRateWindowForTests();
    }

    // ---- 工具 ----

    private String login() {
        return exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("code", "dev-visitor-" + RUN + "-" + userCounter.incrementAndGet()), null)
                .get("token").asText();
    }

    /** 访客端点调用(token 传 null;携带 token 访问不 40100 的用例传入即可) */
    private JsonNode vget(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null, null);
    }

    private JsonNode vpost(String path, Map<String, Object> body, String token) {
        return exchange(HttpMethod.POST, path, token, body, null);
    }

    /** 带自定义 token 的鉴权调用(卡片制作/分享) */
    private JsonNode apost(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, token, body, null);
    }

    private JsonNode adelete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null, null);
    }

    private JsonNode exchange(HttpMethod method, String path, String token,
                             Map<String, Object> body, Map<String, Object> unused) {
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

    private String deviceId() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            builder.append("0123456789abcdef".charAt((int) (Math.random() * 16)));
        }
        return builder.toString();
    }

    private Map<String, Object> letterPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipient", "妈妈");
        payload.put("tone", "TEAR");
        payload.put("paragraphs", FOUR_PARAGRAPHS);
        payload.put("signText", "—— 你的孩子");
        payload.put("styleKey", "NIGHT");
        return payload;
    }

    private Map<String, Object> daysPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "老伴");
        payload.put("startDate", LocalDate.now().minusDays(100).toString());
        payload.put("styleKey", "SUNSET");
        return payload;
    }

    /** 创建指定类型卡片并签发 scene;返回 [scene, cardId, token] */
    private String[] shareCard(String kind, Map<String, Object> payload) {
        String token = login();
        Map<String, Object> request = new HashMap<>();
        request.put("templateKind", kind);
        request.put("payload", payload);
        long cardId = apost("/api/cards", token, request).get("id").asLong();
        String scene = apost("/api/cards/" + cardId + "/share", token, null).get("scene").asText();
        return new String[] {scene, String.valueOf(cardId), token};
    }

    private String[] shareLetterCard() {
        return shareCard("LETTER", letterPayload());
    }

    // ---- 用例 21:落地页 ----

    @Test
    void 访客落地LETTER体面内容为前三段_无第四段_进度为零() {
        String[] shared = shareLetterCard();
        JsonNode view = vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + deviceId(), null);
        assertEquals("LETTER", view.get("templateKind").asText());
        JsonNode publicPayload = view.get("publicPayload");
        assertEquals(3, publicPayload.get("paragraphs").size(), "体面内容=前 3 段");
        assertEquals(FOUR_PARAGRAPHS.get(2), publicPayload.get("paragraphs").get(2).asText());
        assertFalse(serialize(publicPayload).contains(FOUR_PARAGRAPHS.get(3)),
                "第 4 段不进访客可见面内容");
        assertEquals("—— 你的孩子", publicPayload.get("signText").asText());
        assertEquals(0, view.get("hearts").asInt());
        assertFalse(view.get("unlocked").asBoolean());
        assertTrue(view.get("lockedTeaser").asText().length() > 0, "打码钩子有值");
    }

    // ---- 用例 22:点亮心与幂等 ----

    @Test
    void 连点3心解锁_窗口过期后第4次恒3不再计数() {
        String[] shared = shareLetterCard();
        String device = deviceId();
        String path = "/api/visitor/cards/" + shared[0] + "/hearts";
        JsonNode state = null;
        for (int i = 1; i <= 3; i++) {
            state = vpost(path, Map.of("deviceId", device), null);
            assertEquals(i, state.get("hearts").asInt());
        }
        // 第 3 次后:已解锁
        assertTrue(state.get("unlocked").asBoolean());
        assertEquals("已解锁", state.get("remainingHint").asText());
        assertEquals(3, state.get("totalHearts").asInt());

        // unlockedAt 落库
        VisitorUnlockEntity row = visitorUnlockRepository
                .findByCardIdAndDeviceId(Long.parseLong(shared[1]), device).orElseThrow();
        assertTrue(row.getUnlockedAt() != null, "hearts 达 3 落 unlockedAt");
        assertEquals(3, row.getHearts());

        // 幂等:第 4 次调用在窗口重置(模拟过期)后断言恒 3/true 不报错
        visitorService.resetHeartRateWindowForTests();
        JsonNode fourth = vpost(path, Map.of("deviceId", device), null);
        assertEquals(3, fourth.get("hearts").asInt(), "已满 3 再调恒 3");
        assertTrue(fourth.get("unlocked").asBoolean());
        assertEquals("已解锁", fourth.get("remainingHint").asText());
        assertEquals(3, fourth.get("totalHearts").asInt(), "第 4 次不再计数");
    }

    // ---- 用例 23:deviceId 与 scene 校验、设备隔离、软删 ----

    @Test
    void 设备B独立进度_scene无效与软删统一40400_deviceId非法40000() {
        String[] shared = shareLetterCard();
        String deviceA = deviceId();
        String deviceB = deviceId();
        String path = "/api/visitor/cards/" + shared[0] + "/hearts";
        for (int i = 0; i < 3; i++) {
            vpost(path, Map.of("deviceId", deviceA), null);
        }
        JsonNode b = vpost(path, Map.of("deviceId", deviceB), null);
        assertEquals(1, b.get("hearts").asInt(), "deviceId B 独立进度(A 解锁不影响 B)");

        JsonNode bView = vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + deviceB, null);
        assertEquals(1, bView.get("hearts").asInt());
        assertFalse(bView.get("unlocked").asBoolean());

        // deviceId 缺失/31 字/65 字 → 40000
        assertErrorCode(vget("/api/visitor/cards/" + shared[0], null), "40000");
        assertErrorCode(vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + "a".repeat(31), null),
                "40000");
        assertErrorCode(vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + "a".repeat(65), null),
                "40000");

        // scene 乱码 → 40400
        assertErrorCode(vget("/api/visitor/cards/!!!!@@@@?deviceId=" + deviceB, null), "40400");
        assertErrorCode(vget("/api/visitor/cards/abcdefgh?deviceId=" + deviceB, null), "40400");

        // 卡片软删后同 scene → 40400
        adelete("/api/cards/" + shared[1], shared[2]);
        assertErrorCode(vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + deviceB, null), "40400");
    }

    // ---- 用例 24:软频控 42900 ----

    @Test
    void 分钟内第4次点心42900_窗口重置后可再点() {
        String[] shared = shareLetterCard();
        String device = deviceId();
        String path = "/api/visitor/cards/" + shared[0] + "/hearts";
        vpost(path, Map.of("deviceId", device), null);
        vpost(path, Map.of("deviceId", device), null);
        vpost(path, Map.of("deviceId", device), null);
        // 第 4 次/分钟连点 → 42900(HTTP 200,业务码断言)
        assertErrorCode(vpost(path, Map.of("deviceId", device), null), "42900");
        // 窗口过期后可再点(测试以重置窗口模拟)
        visitorService.resetHeartRateWindowForTests();
        JsonNode again = vpost(path, Map.of("deviceId", device), null);
        assertTrue(again.has("hearts"), "窗口过期后可再点,不阻断解锁目标");
        assertEquals(3, again.get("hearts").asInt());
        assertTrue(again.get("unlocked").asBoolean());
    }

    // ---- 用例 25:内容门控 ----

    @Test
    void 未解锁取内容40900_解锁后LETTER完整段落与DAYS_CARD隐藏句() {
        String[] letter = shareLetterCard();
        String device = deviceId();
        String contentPath = "/api/visitor/cards/" + letter[0] + "/content?deviceId=" + device;
        assertErrorCode(vget(contentPath, null), "40900");
        for (int i = 0; i < 3; i++) {
            vpost("/api/visitor/cards/" + letter[0] + "/hearts", Map.of("deviceId", device), null);
        }
        JsonNode letterContent = vget(contentPath, null);
        assertEquals("LETTER", letterContent.get("templateKind").asText());
        JsonNode hidden = letterContent.get("hiddenContent");
        assertEquals(4, hidden.get("paragraphs").size(), "解锁后=完整定稿段落");
        assertEquals(FOUR_PARAGRAPHS.get(3), hidden.get("paragraphs").get(3).asText());
        assertEquals("—— 你的孩子", hidden.get("signText").asText());
        assertTrue(letterContent.get("ctaCopy").get("buttonText").asText().length() > 0);

        // DAYS_CARD:hiddenLine 含正确天数(起始日=100 天前 → 100)
        String[] days = shareCard("DAYS_CARD", daysPayload());
        String daysDevice = deviceId();
        String daysContentPath = "/api/visitor/cards/" + days[0] + "/content?deviceId=" + daysDevice;
        for (int i = 0; i < 3; i++) {
            vpost("/api/visitor/cards/" + days[0] + "/hearts", Map.of("deviceId", daysDevice), null);
        }
        JsonNode daysContent = vget(daysContentPath, null);
        String hiddenLine = daysContent.get("hiddenContent").get("hiddenLine").asText();
        assertTrue(hiddenLine.contains("100"), "隐藏句含正确天数: " + hiddenLine);
    }

    // ---- 用例 26:CAPSULE 邀请函内容门控 ----

    @Test
    void 胶囊邀请函_开箱前访客只见悬念文案_开箱日后见真实信件() {
        String token = login();
        Map<String, Object> capsuleRequest = new HashMap<>();
        capsuleRequest.put("type", "SELF");
        capsuleRequest.put("openRule", "GAOKAO_SCORE_DAY");
        capsuleRequest.put("openDate", LocalDate.now().plusDays(10).toString());
        capsuleRequest.put("coverHint", "一年不见，你还挺想我吧");
        capsuleRequest.put("contentText", "绝密封存内容写给未来");
        capsuleRequest.put("photoUrl", "https://cdn.example.test/p.png");
        long capsuleId = apost("/api/capsules", token, capsuleRequest).get("id").asLong();

        Map<String, Object> payload = new HashMap<>();
        payload.put("coverHint", "封面");
        payload.put("openDate", LocalDate.now().plusDays(10).toString());
        payload.put("type", "SELF");
        Map<String, Object> cardRequest = new HashMap<>();
        cardRequest.put("templateKind", "CAPSULE");
        cardRequest.put("payload", payload);
        cardRequest.put("capsuleId", capsuleId);
        long cardId = apost("/api/cards", token, cardRequest).get("id").asLong();
        String scene = apost("/api/cards/" + cardId + "/share", token, null).get("scene").asText();

        String device = deviceId();
        // 开箱日前:访客视图=封面提示+倒计时,无信件本体
        JsonNode view = vget("/api/visitor/cards/" + scene + "?deviceId=" + device, null);
        JsonNode publicPayload = view.get("publicPayload");
        assertEquals("一年不见，你还挺想我吧", publicPayload.get("coverHint").asText());
        assertEquals(10, publicPayload.get("daysUntilOpen").asInt());
        assertFalse(serialize(view).contains("绝密封存内容写给未来"), "封存内容不进访客落地视图");

        // 解锁后 content=悬念文案(真实内容零泄露)
        for (int i = 0; i < 3; i++) {
            vpost("/api/visitor/cards/" + scene + "/hearts", Map.of("deviceId", device), null);
        }
        JsonNode content = vget("/api/visitor/cards/" + scene + "/content?deviceId=" + device, null);
        JsonNode hidden = content.get("hiddenContent");
        assertEquals(CardTextLib.CAPSULE_SUSPENSE, hidden.get("contentText").asText());
        assertFalse(serialize(content).contains("绝密封存内容写给未来"), "开箱日前真实内容零泄露");

        // 构造 openDate 已过 → content=真实信件+照片
        TimeCapsuleEntity capsule = capsuleRepository.findById(capsuleId).orElseThrow();
        capsule.setOpenDate(LocalDate.now().minusDays(1));
        capsuleRepository.saveAndFlush(capsule);
        JsonNode revealed = vget("/api/visitor/cards/" + scene + "/content?deviceId=" + device, null);
        assertEquals("绝密封存内容写给未来", revealed.get("hiddenContent").get("contentText").asText());
        assertEquals("https://cdn.example.test/p.png", revealed.get("hiddenContent").get("photoUrl").asText());
    }

    // ---- 用例 27:携带 token 访问 visitor 端点照常 200 ----

    @Test
    void 携带token访问访客端点不因token报错() {
        String[] shared = shareLetterCard();
        String token = login();
        JsonNode view = vget("/api/visitor/cards/" + shared[0] + "?deviceId=" + deviceId(), token);
        assertEquals("LETTER", view.get("templateKind").asText(), "不因 token 40100");
        JsonNode state = vpost("/api/visitor/cards/" + shared[0] + "/hearts",
                Map.of("deviceId", deviceId()), token);
        assertEquals(1, state.get("hearts").asInt());
    }

    // ---- 用例 27(v1.8.0):BIRTHDAY_CARD 访客端形态,对齐 v2.2-family-design.md §3 ----

    @Test
    void 福卡访客体面内容relation加photoUrls加kicker_素材删除跳过_3心解锁blessingText() {
        String token = login();
        String inviteCode = apost("/api/family", token, Map.of("name", "念家时"))
                .get("inviteCode").asText();
        apost("/api/family/join", login(), Map.of("inviteCode", inviteCode));
        // 生日日子 + 2 条素材池瞬间
        Map<String, Object> day = new HashMap<>();
        day.put("relation", "妈妈");
        day.put("kind", "BIRTHDAY");
        day.put("calendarType", "SOLAR");
        day.put("month", LocalDate.now().plusDays(30).getMonthValue());
        day.put("day", LocalDate.now().plusDays(30).getDayOfMonth());
        day.put("visibility", "PRIVATE");
        long dayId = apost("/api/days", token, day).get("id").asLong();
        long momentOne = publishMoment(token, "素材一");
        long momentTwo = publishMoment(token, "素材二");
        apost("/api/moments/" + momentOne + "/collect", token, Map.of("dayId", dayId));
        apost("/api/moments/" + momentTwo + "/collect", token, Map.of("dayId", dayId));

        Map<String, Object> payload = new HashMap<>();
        payload.put("dayId", dayId);
        payload.put("relation", "妈妈");
        payload.put("blessingText", "藏起来的生日祝福语");
        payload.put("momentIds", List.of(momentOne, momentTwo));
        payload.put("styleKey", "MINT");
        Map<String, Object> request = new HashMap<>();
        request.put("templateKind", "BIRTHDAY_CARD");
        request.put("payload", payload);
        long cardId = apost("/api/cards", token, request).get("id").asLong();
        String scene = apost("/api/cards/" + cardId + "/share", token, null).get("scene").asText();

        // 体面内容:relation+photoUrls(≤3)+kicker「生日快乐」;无 blessingText 泄露
        JsonNode view = vget("/api/visitor/cards/" + scene + "?deviceId=" + deviceId(), null);
        assertEquals("BIRTHDAY_CARD", view.get("templateKind").asText());
        JsonNode publicPayload = view.get("publicPayload");
        assertEquals("妈妈", publicPayload.get("relation").asText());
        assertEquals("生日快乐", publicPayload.get("kicker").asText());
        assertEquals(2, publicPayload.get("photoUrls").size(), "两条素材的 capability 路径");
        assertTrue(publicPayload.get("photoUrls").get(0).asText().startsWith("/api/photos/"));
        assertEquals("MINT", publicPayload.get("styleKey").asText());
        assertTrue(view.get("lockedTeaser").asText().length() > 0, "打码钩子有值");
        assertFalse(serialize(view).contains("藏起来的生日祝福语"), "祝福语不进体面内容");

        // 未解锁取内容 → 40900
        String device = deviceId();
        assertErrorCode(vget("/api/visitor/cards/" + scene + "/content?deviceId=" + device, null), "40900");

        // 素材瞬间被删后 photoUrls 跳过该项(已存档 payload 不追溯)
        adelete("/api/moments/" + momentOne, token);
        JsonNode afterDelete = vget("/api/visitor/cards/" + scene + "?deviceId=" + deviceId(), null);
        assertEquals(1, afterDelete.get("publicPayload").get("photoUrls").size(),
                "已删素材的 photoUrl 跳过");

        // 3 心解锁 → blessingText
        for (int i = 0; i < 3; i++) {
            vpost("/api/visitor/cards/" + scene + "/hearts", Map.of("deviceId", device), null);
        }
        JsonNode content = vget("/api/visitor/cards/" + scene + "/content?deviceId=" + device, null);
        assertEquals("BIRTHDAY_CARD", content.get("templateKind").asText());
        assertEquals("藏起来的生日祝福语", content.get("hiddenContent").get("blessingText").asText());
        assertTrue(content.get("ctaCopy").get("buttonText").asText().length() > 0);
    }

    // ---- 私有工具 ----

    private long publishMoment(String token, String text) {
        byte[] data = new byte[8];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        Map<String, Object> upload = new HashMap<>();
        upload.put("imageBase64", java.util.Base64.getEncoder().encodeToString(data));
        upload.put("scene", "MOMENT");
        String photoId = apost("/api/photos", token, upload).get("photoId").asText();
        return apost("/api/moments", token, Map.of("photoId", photoId, "text", text))
                .get("id").asLong();
    }

    private String serialize(JsonNode node) {
        return node == null ? "" : node.toString();
    }
}
