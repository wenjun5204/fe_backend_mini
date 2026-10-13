package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 家人瞬间组集成测试(H2 内存库):发布频控/仅本人可删(隐私红线)/真好/收藏进日子/素材池。
 * 用例矩阵对齐 docs/design/v2.2-family-design.md §3 moments 组(用例 7-15 + photos 组用例 6 的绑定端越权)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MomentsApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();
    private static final String RUN = Long.toHexString(System.nanoTime());

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return post("/api/auth/login", null, Map.of("code",
                "dev-moments-" + RUN + "-" + userCounter.incrementAndGet()))
                .get("token").asText();
    }

    /** 独立用户各自建一个家:返回 [户主token, 成员token] */
    private String[] family() {
        String owner = login();
        String inviteCode = post("/api/family", owner, Map.of("name", "念家时")).get("inviteCode").asText();
        String member = login();
        post("/api/family/join", member, Map.of("inviteCode", inviteCode));
        return new String[] {owner, member};
    }

    /** 8 字节 JPEG 头载荷(嗅探通过,打频控最快) */
    private byte[] jpegBytes() {
        byte[] data = new byte[8];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private String uploadPhoto(String token, String scene) {
        return post("/api/photos", token, Map.of(
                "imageBase64", java.util.Base64.getEncoder().encodeToString(jpegBytes()),
                "scene", scene)).get("photoId").asText();
    }

    private JsonNode createMoment(String token, String photoId, String text) {
        return post("/api/moments", token, Map.of("photoId", photoId, "text", text));
    }

    private long createDay(String token, String kind, String visibility) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", "妈妈");
        payload.put("kind", kind);
        payload.put("calendarType", "SOLAR");
        payload.put("month", 5);
        payload.put("day", 20);
        payload.put("visibility", visibility);
        return post("/api/days", token, payload).get("id").asLong();
    }

    private String nickname(String token) {
        return userRepository.findById(Long.parseLong(token)).orElseThrow().getNickname();
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
        try {
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + response, e);
        }
    }

    private void assertErrorCode(JsonNode response, String code) {
        assertEquals(code, response.get("code").asText(), "响应: " + response);
    }

    // ---- 用例 7:发布主路径与家族隔离 ----

    @Test
    void 发布主路径全字段回显_家族内可见_他家族不在列表() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        String photoId = uploadPhoto(owner, "MOMENT");
        JsonNode moment = createMoment(owner, photoId, "妈妈在院子里晒被子");

        assertEquals(owner, String.valueOf(moment.get("userId").asLong()));
        assertEquals(photoId, moment.get("photoId").asText());
        assertEquals("/api/photos/" + photoId, moment.get("photoUrl").asText());
        assertEquals("妈妈在院子里晒被子", moment.get("text").asText());
        assertEquals(0, moment.get("likeCount").asInt());
        assertFalse(moment.get("likedByMe").asBoolean());
        assertEquals(0, moment.get("collectedCount").asInt());
        assertTrue(moment.get("collectedByMeDayIds").isEmpty());
        assertTrue(moment.get("createdAt").asText().length() > 0);

        // 户主与另一成员时间线均可见
        assertEquals(1, get("/api/moments", owner).get("items").size());
        JsonNode memberView = get("/api/moments", member);
        assertEquals(1, memberView.get("items").size());
        assertEquals(moment.get("id").asLong(), memberView.get("items").get(0).get("id").asLong());
        assertFalse(memberView.get("items").get(0).get("likedByMe").asBoolean(), "他人视角 likedByMe=false");

        // 其他家族账号不在列表(不报错,空状态)
        String[] otherFamily = family();
        assertEquals(0, get("/api/moments", otherFamily[0]).get("items").size());
    }

    // ---- 用例 8:无家族降级 ----

    @Test
    void 无家族发布40900_时间线空数组() {
        String loner = login();
        JsonNode rejection = createMoment(loner, "whatever-photo", "没有家的瞬间");
        assertErrorCode(rejection, "40900");
        JsonNode timeline = get("/api/moments", loner);
        assertEquals(0, timeline.get("items").size());
        assertFalse(timeline.has("nextCursor"), "无家族无游标");
    }

    // ---- 用例 9:频控与文本校验 ----

    @Test
    void 当日第11条40300_删1条再发仍计满_文本越界与违禁词40000() {
        String[] family = family();
        String owner = family[0];
        for (int i = 0; i < 10; i++) {
            JsonNode moment = createMoment(owner, uploadPhoto(owner, "MOMENT"), "第 " + (i + 1) + " 条瞬间");
            assertFalse(moment.has("code"), "前 10 条必须全部成功: " + moment);
        }
        assertErrorCode(createMoment(owner, uploadPhoto(owner, "MOMENT"), "第 11 条"), "40300");
        // 软删行仍计数:删 1 条后再发依旧计满(防「删了再发」洗额度)
        long firstId = get("/api/moments", owner).get("items").get(0).get("id").asLong();
        assertTrue(delete("/api/moments/" + firstId, owner).get("deleted").asBoolean());
        assertErrorCode(createMoment(owner, uploadPhoto(owner, "MOMENT"), "删了再发"), "40300");
        // 其他成员不受影响(按 userId+日期计数)
        assertFalse(createMoment(family[1], uploadPhoto(family[1], "MOMENT"), "成员正常发").has("code"));

        // text 61 字 / 违禁词 → 40000(独立用户,避开频控)
        String fresh = family()[0];
        assertErrorCode(createMoment(fresh, uploadPhoto(fresh, "MOMENT"), "长".repeat(61)), "40000");
        assertErrorCode(createMoment(fresh, uploadPhoto(fresh, "MOMENT"), "这条是运势预测"), "40000");
        assertErrorCode(post("/api/moments", fresh, Map.of("photoId", uploadPhoto(fresh, "MOMENT"),
                "text", "  ")), "40000");
    }

    // ---- 用例 10:时间线分页 ----

    @Test
    void 时间线倒序_beforeId翻页正确_nextCursor末端缺省_limit越界40000() {
        String owner = family()[0];
        long first = createMoment(owner, uploadPhoto(owner, "MOMENT"), "第一条").get("id").asLong();
        long second = createMoment(owner, uploadPhoto(owner, "MOMENT"), "第二条").get("id").asLong();
        long third = createMoment(owner, uploadPhoto(owner, "MOMENT"), "第三条").get("id").asLong();

        // 默认 10 条:3 条全量,倒序,末端无 nextCursor
        JsonNode page = get("/api/moments", owner);
        assertEquals(3, page.get("items").size());
        assertEquals(third, page.get("items").get(0).get("id").asLong(), "倒序");
        assertEquals(second, page.get("items").get(1).get("id").asLong());
        assertEquals(first, page.get("items").get(2).get("id").asLong());
        assertFalse(page.has("nextCursor"), "没有更早时 nextCursor 缺省");

        // limit=2:首页 2 条 + nextCursor,翻页取到剩余 1 条且无游标
        JsonNode pageOne = get("/api/moments?limit=2", owner);
        assertEquals(2, pageOne.get("items").size());
        assertEquals(third, pageOne.get("items").get(0).get("id").asLong());
        assertEquals(second, pageOne.get("nextCursor").asLong(), "nextCursor=下页 beforeId");
        JsonNode pageTwo = get("/api/moments?beforeId=" + pageOne.get("nextCursor").asLong() + "&limit=2", owner);
        assertEquals(1, pageTwo.get("items").size());
        assertEquals(first, pageTwo.get("items").get(0).get("id").asLong());
        assertFalse(pageTwo.has("nextCursor"), "末端缺省");

        // limit 越界
        assertErrorCode(get("/api/moments?limit=0", owner), "40000");
        assertErrorCode(get("/api/moments?limit=21", owner), "40000");
    }

    // ---- 用例 11:删除(隐私权红线) ----

    @Test
    void 删除仅本人_户主与家人删他人均40400_本人删联动照片40400且素材池消失_重复删幂等() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        long momentId = createMoment(member, uploadPhoto(member, "MOMENT"), "成员发的瞬间").get("id").asLong();
        String photoId = get("/api/moments", member).get("items").get(0).get("photoId").asText();

        // 家人(户主)删成员瞬间 → 40400 不泄露存在性(隐私红线:校验只认 userId,不认角色)
        assertErrorCode(delete("/api/moments/" + momentId, owner), "40400");
        // 另一位家人同样不可删
        assertErrorCode(delete("/api/moments/" + momentId, login()), "40400");
        // 不存在 id 同文案
        assertErrorCode(delete("/api/moments/99999999", owner), "40400");

        // 本人删:时间线消失、照片回图 40400
        assertTrue(delete("/api/moments/" + momentId, member).get("deleted").asBoolean());
        assertEquals(0, get("/api/moments", member).get("items").size());
        assertErrorCode(parse(getPhotoBody(photoId)), "40400");
        // 重复删幂等
        assertTrue(delete("/api/moments/" + momentId, member).get("deleted").asBoolean());

        // 素材池无该条:先收藏再删,素材池为空
        String fresh[] = family();
        long freshId = createMoment(fresh[0], uploadPhoto(fresh[0], "MOMENT"), "素材池瞬间").get("id").asLong();
        long dayId = createDay(fresh[0], "BIRTHDAY", "PRIVATE");
        post("/api/moments/" + freshId + "/collect", fresh[0], Map.of("dayId", dayId));
        assertEquals(1, get("/api/days/" + dayId + "/moments", fresh[0]).get("items").size());
        assertTrue(delete("/api/moments/" + freshId, fresh[0]).get("deleted").asBoolean());
        assertEquals(0, get("/api/days/" + dayId + "/moments", fresh[0]).get("items").size(),
                "瞬间删除后素材池条目即时消失");
    }

    // ---- 用例 12:真好 ----

    @Test
    void 真好限1次_重复POST不增数_DELETE减1再删幂等_他家族40400() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        long momentId = createMoment(owner, uploadPhoto(owner, "MOMENT"), "真好我").get("id").asLong();

        JsonNode first = post("/api/moments/" + momentId + "/like", member, null);
        assertEquals(1, first.get("likeCount").asInt());
        assertTrue(first.get("likedByMe").asBoolean());
        JsonNode repeat = post("/api/moments/" + momentId + "/like", member, null);
        assertEquals(1, repeat.get("likeCount").asInt(), "重复 POST 幂等不增数");
        assertTrue(repeat.get("likedByMe").asBoolean());

        JsonNode unlike = delete("/api/moments/" + momentId + "/like", member);
        assertEquals(0, unlike.get("likeCount").asInt());
        assertFalse(unlike.get("likedByMe").asBoolean());
        // 再 DELETE 幂等
        JsonNode reUnlike = delete("/api/moments/" + momentId + "/like", member);
        assertEquals(0, reUnlike.get("likeCount").asInt());
        assertFalse(reUnlike.get("likedByMe").asBoolean());

        // 互动态聚合:成员点赞后,户主视角 likeCount=1、本人视角 likedByMe=true
        post("/api/moments/" + momentId + "/like", member, null);
        JsonNode ownerView = get("/api/moments", owner).get("items").get(0);
        assertEquals(1, ownerView.get("likeCount").asInt());
        assertFalse(ownerView.get("likedByMe").asBoolean());
        JsonNode memberView = get("/api/moments", member).get("items").get(0);
        assertTrue(memberView.get("likedByMe").asBoolean());

        // 他家族瞬间 like → 40400
        String[] otherFamily = family();
        long otherMomentId = createMoment(otherFamily[0], uploadPhoto(otherFamily[0], "MOMENT"),
                "别家的瞬间").get("id").asLong();
        assertErrorCode(post("/api/moments/" + otherMomentId + "/like", owner, null), "40400");
        assertErrorCode(delete("/api/moments/" + otherMomentId + "/like", owner), "40400");
    }

    // ---- 用例 13:收藏进日子 ----

    @Test
    void 收藏到本人BIRTHDAY日子成功_幂等_家人共享素材_非BIRTHDAY与他人PRIVATE被拒() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        long ownerMoment = createMoment(owner, uploadPhoto(owner, "MOMENT"), "户主的瞬间").get("id").asLong();
        long birthdayDay = createDay(owner, "BIRTHDAY", "FAMILY");
        long anniversaryDay = createDay(owner, "ANNIVERSARY", "FAMILY");
        long togetherDay = createDay(owner, "TOGETHER", "FAMILY");
        long memberPrivateDay = createDay(member, "BIRTHDAY", "PRIVATE");

        // 本人收藏成功
        JsonNode collected = post("/api/moments/" + ownerMoment + "/collect", owner, Map.of("dayId", birthdayDay));
        assertEquals(1, collected.get("collectedCount").asInt());
        assertTrue(collected.get("collectedByMe").asBoolean());
        assertEquals(birthdayDay, collected.get("dayId").asLong());
        // 重复 POST 幂等(不增数)
        JsonNode repeat = post("/api/moments/" + ownerMoment + "/collect", owner, Map.of("dayId", birthdayDay));
        assertEquals(1, repeat.get("collectedCount").asInt());

        // 家人 B 收藏 A 的瞬间到同一日子成功(共享素材,collectedCount 跨家人累计)
        JsonNode shared = post("/api/moments/" + ownerMoment + "/collect", member,
                Map.of("dayId", birthdayDay));
        assertEquals(2, shared.get("collectedCount").asInt());
        assertTrue(shared.get("collectedByMe").asBoolean());

        // 时间线 collectedByMeDayIds 驱动按钮态
        JsonNode memberView = get("/api/moments", member).get("items").get(0);
        assertEquals(2, memberView.get("collectedCount").asInt());
        List.of(birthdayDay).forEach(dayId ->
                assertTrue(memberView.get("collectedByMeDayIds").toString().contains(String.valueOf(dayId)),
                        "collectedByMeDayIds 含收藏日子"));
        JsonNode ownerView = get("/api/moments", owner).get("items").get(0);
        assertFalse(ownerView.get("collectedByMeDayIds").toString().isEmpty());

        // 非 BIRTHDAY 日子 → 40000
        assertErrorCode(post("/api/moments/" + ownerMoment + "/collect", owner,
                Map.of("dayId", anniversaryDay)), "40000");
        assertErrorCode(post("/api/moments/" + ownerMoment + "/collect", owner,
                Map.of("dayId", togetherDay)), "40000");
        // 他人 PRIVATE 日子 → 40400(不泄露存在性):户主收藏到成员的 PRIVATE 日子
        assertErrorCode(post("/api/moments/" + ownerMoment + "/collect", owner,
                Map.of("dayId", memberPrivateDay)), "40400");
        // dayId 缺失 → 40000
        assertErrorCode(post("/api/moments/" + ownerMoment + "/collect", owner, Map.of()), "40000");
        // 他家族瞬间 → 40400
        String[] otherFamily = family();
        long otherMoment = createMoment(otherFamily[0], uploadPhoto(otherFamily[0], "MOMENT"),
                "别家瞬间").get("id").asLong();
        assertErrorCode(post("/api/moments/" + otherMoment + "/collect", owner,
                Map.of("dayId", birthdayDay)), "40400");
    }

    // ---- 用例 14:取消收藏 ----

    @Test
    void 取消收藏幂等_素材池与collectedCount即时反映() {
        String[] family = family();
        String owner = family[0];
        long momentId = createMoment(owner, uploadPhoto(owner, "MOMENT"), "要取消收藏的瞬间").get("id").asLong();
        long dayId = createDay(owner, "BIRTHDAY", "PRIVATE");
        post("/api/moments/" + momentId + "/collect", owner, Map.of("dayId", dayId));
        assertEquals(1, get("/api/days/" + dayId + "/moments", owner).get("items").size());

        JsonNode uncollected = delete("/api/moments/" + momentId + "/collect?dayId=" + dayId, owner);
        assertEquals(0, uncollected.get("collectedCount").asInt());
        assertFalse(uncollected.get("collectedByMe").asBoolean());
        assertEquals(dayId, uncollected.get("dayId").asLong());
        assertEquals(0, get("/api/days/" + dayId + "/moments", owner).get("items").size(),
                "素材池即时反映");
        assertTrue(get("/api/moments", owner).get("items").get(0).get("collectedByMeDayIds").isEmpty(),
                "collectedByMeDayIds 即时反映");
        // 再取消幂等;dayId 缺失 40000
        JsonNode repeat = delete("/api/moments/" + momentId + "/collect?dayId=" + dayId, owner);
        assertEquals(0, repeat.get("collectedCount").asInt());
        assertErrorCode(delete("/api/moments/" + momentId + "/collect", owner), "40000");
    }

    // ---- 用例 15:素材池 ----

    @Test
    void 素材池含全家人收藏_昵称正确_非BIRTHDAY与不可见被拒_已删瞬间消失() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        long birthdayDay = createDay(owner, "BIRTHDAY", "FAMILY");
        long ownerMoment = createMoment(owner, uploadPhoto(owner, "MOMENT"), "户主收藏的瞬间").get("id").asLong();
        long memberMoment = createMoment(member, uploadPhoto(member, "MOMENT"), "成员收藏的瞬间").get("id").asLong();

        post("/api/moments/" + ownerMoment + "/collect", owner, Map.of("dayId", birthdayDay));
        // 家人 B 收藏 A 的瞬间到同一日子(共享素材)
        post("/api/moments/" + ownerMoment + "/collect", member, Map.of("dayId", birthdayDay));
        post("/api/moments/" + memberMoment + "/collect", member, Map.of("dayId", birthdayDay));

        JsonNode pool = get("/api/days/" + birthdayDay + "/moments", owner);
        assertEquals(3, pool.get("total").asInt());
        assertEquals(3, pool.get("items").size());
        // id 倒序≈collectedAt 倒序:最后收藏的在前
        assertEquals(memberMoment, pool.get("items").get(0).get("momentId").asLong());
        JsonNode first = pool.get("items").get(0);
        assertTrue(first.get("photoUrl").asText().startsWith("/api/photos/"),
                "capability 路径: " + first.get("photoUrl"));
        assertEquals("成员收藏的瞬间", first.get("text").asText());
        assertEquals(nickname(member), first.get("authorName").asText(), "发布人昵称服务端解析");
        assertEquals(nickname(member), first.get("collectedBy").asText(), "收藏人昵称");
        assertTrue(first.get("collectedAt").asText().length() > 0);

        JsonNode second = pool.get("items").get(1);
        assertEquals(ownerMoment, second.get("momentId").asLong());
        assertEquals(nickname(owner), second.get("authorName").asText());
        assertEquals(nickname(member), second.get("collectedBy").asText(), "家人收藏他人瞬间");

        // 非 BIRTHDAY 日子 → 40000;本人不可见(他人 PRIVATE) → 40400
        long anniversaryDay = createDay(owner, "ANNIVERSARY", "FAMILY");
        assertErrorCode(get("/api/days/" + anniversaryDay + "/moments", owner), "40000");
        long memberPrivateDay = createDay(member, "BIRTHDAY", "PRIVATE");
        assertErrorCode(get("/api/days/" + memberPrivateDay + "/moments", owner), "40400");
        assertErrorCode(get("/api/days/99999999/moments", owner), "40400");

        // 瞬间被删后条目即时消失
        assertTrue(delete("/api/moments/" + memberMoment, member).get("deleted").asBoolean());
        JsonNode after = get("/api/days/" + birthdayDay + "/moments", owner);
        assertEquals(2, after.get("total").asInt());
        for (JsonNode item : after.get("items")) {
            assertFalse(item.get("momentId").asLong() == memberMoment, "已删瞬间不在素材池");
        }
    }

    // ---- photos 组用例 6:绑定端越权(三重校验) ----

    @Test
    void photoId越权_他人scene不符已绑定均40000() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        // 他人 photoId 发瞬间 → 40000
        String memberPhoto = uploadPhoto(member, "MOMENT");
        assertErrorCode(createMoment(owner, memberPhoto, "拿别人的照片"), "40000");
        // scene=CAPSULE 的 photoId 发瞬间 → 40000
        String capsulePhoto = uploadPhoto(owner, "CAPSULE");
        assertErrorCode(createMoment(owner, capsulePhoto, "用途不符"), "40000");
        // 已绑定(先发过瞬间) → 40000
        String boundPhoto = uploadPhoto(owner, "MOMENT");
        assertFalse(createMoment(owner, boundPhoto, "第一次绑定成功").has("code"));
        assertErrorCode(createMoment(owner, boundPhoto, "二次绑定"), "40000");
        // 不存在的 photoId → 40000(绑定端参数口径,非 404)
        assertErrorCode(createMoment(owner, "zzzzzzzzzzzzzzzzzz", "照片不存在"), "40000");
    }

    // ---- 私有工具 ----

    private String getPhotoBody(String photoId) {
        return rest.get().uri("/api/photos/" + photoId).retrieve().body(String.class);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }
}
