package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.service.CalendarConvertCore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 日子管家 7 端点集成测试(H2 内存库,登录走真实链路)。
 * 用例矩阵对齐 docs/design/v2.0-days-design.md §3:主路径+越权+校验失败+农历三十无落点口径。
 * HTTP 客户端用 RestClient:Boot 4.1 的 TestRestTemplate 依赖 spring-boot-restclient 模块(本工程未引入),
 * 行为等价——仍是真实端口真实请求。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DaysApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    /** static:JUnit 每个用例新建实例,计数器必须跨用例累积,保证每用例拿到全新用户 */
    private static final AtomicInteger userCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return login("dev-days-" + userCounter.incrementAndGet());
    }

    private String login(String code) {
        return post("/api/auth/login", null, Map.of("code", code)).get("token").asText();
    }

    /** 独立用户各自建一个家:返回 [户主token, 成员token, 第二成员token] */
    private String[] family() {
        String owner = login();
        String inviteCode = createFamily(owner);
        String member = login();
        joinFamily(member, inviteCode);
        String member2 = login();
        joinFamily(member2, inviteCode);
        return new String[] { owner, member, member2 };
    }

    private String createFamily(String token) {
        JsonNode family = post("/api/family", token, Map.of("name", "念家时"));
        return family.get("inviteCode").asText();
    }

    private void joinFamily(String token, String inviteCode) {
        post("/api/family/join", token, Map.of("inviteCode", inviteCode));
    }

    private Map<String, Object> dayPayload(String relation, String kind, String calendarType,
                                           Integer month, Integer day, String visibility,
                                           List<Integer> reminders) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("relation", relation);
        payload.put("kind", kind);
        payload.put("calendarType", calendarType);
        if (month != null) {
            payload.put("month", month);
        }
        if (day != null) {
            payload.put("day", day);
        }
        payload.put("visibility", visibility);
        if (reminders != null) {
            payload.put("reminders", reminders);
        }
        return payload;
    }

    /** 落点在 basis+N 天的 SOLAR 月日 */
    private int[] monthDayAfter(long days) {
        LocalDate date = LocalDate.now().plusDays(days);
        return new int[] { date.getMonthValue(), date.getDayOfMonth() };
    }

    /** v1.8.0 year 扩展:带年份的日子载荷(其余字段同 dayPayload) */
    private Map<String, Object> yearPayload(String relation, String kind, Integer month, Integer day,
                                             Integer year) {
        Map<String, Object> payload = dayPayload(relation, kind, "SOLAR", month, day, "PRIVATE", null);
        if (year != null) {
            payload.put("year", year);
        }
        return payload;
    }

    private JsonNode get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private JsonNode post(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private JsonNode put(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, path, token, body);
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

    private long createDay(String token, Map<String, Object> payload) {
        return post("/api/days", token, payload).get("id").asLong();
    }

    // ---- GET /api/days ----

    @Test
    void 空账号日子列表为空() {
        JsonNode list = get("/api/days", login());
        assertEquals(0, list.get("items").size());
    }

    @Test
    void 列表只见本人与同家族他人FAMILY_且按落点升序TOGETHER压底() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        int[] plus2 = monthDayAfter(2);
        long anniversary = createDay(owner, dayPayload("结婚纪念日", "ANNIVERSARY", "SOLAR",
                plus2[0], plus2[1], "PRIVATE", null));
        int[] plus10 = monthDayAfter(10);
        long birthday = createDay(owner, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus10[0], plus10[1], "PRIVATE", null));
        int[] plus5 = monthDayAfter(5);
        long memberFamilyDay = createDay(member, dayPayload("亲家", "BIRTHDAY", "SOLAR",
                plus5[0], plus5[1], "FAMILY", null));
        int[] plus3 = monthDayAfter(3);
        long memberPrivateDay = createDay(member, dayPayload("老战友", "ANNIVERSARY", "SOLAR",
                plus3[0], plus3[1], "PRIVATE", null));
        long together = createDay(owner, dayPayload("老伴", "TOGETHER", "SOLAR", null, null, "PRIVATE", null));

        JsonNode items = get("/api/days", owner).get("items");
        assertEquals(4, items.size());
        assertEquals(anniversary, items.get(0).get("id").asLong());
        assertEquals(2, items.get(0).get("daysUntil").asInt());
        assertEquals(memberFamilyDay, items.get(1).get("id").asLong());
        assertEquals(birthday, items.get(2).get("id").asLong());
        assertEquals(together, items.get(3).get("id").asLong());
        assertFalse(containsId(items, memberPrivateDay), "他人 PRIVATE 不可见");

        JsonNode memberItems = get("/api/days", member).get("items");
        assertTrue(containsId(memberItems, memberPrivateDay), "本人 PRIVATE 自见");
        assertTrue(containsId(memberItems, memberFamilyDay));
        assertFalse(containsId(memberItems, anniversary), "他人 PRIVATE 不可见");
        assertFalse(containsId(memberItems, together));
    }

    private boolean containsId(JsonNode items, long id) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    @Test
    void 未登录访问日子列表被拒() {
        assertErrorCode(get("/api/days", null), "40100");
    }

    // ---- POST /api/days ----

    @Test
    void 公历生日主路径_落点与农历副文本正确() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        JsonNode day = post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));

        assertEquals(LocalDate.now().plusDays(7).toString(), day.get("nextDate").asText());
        assertEquals(7, day.get("daysUntil").asInt());
        assertTrue(day.get("nextDateLunarText").asText().startsWith("农历"), day.get("nextDateLunarText").asText());
        assertEquals("👩", day.get("emoji").asText(), "relation=妈妈 缺省 emoji 预置映射");
        assertEquals(0, day.get("reminders").size());
    }

    @Test
    void 农历日子落在下一非闰月同日() {
        String token = login();
        JsonNode day = post("/api/days", token, dayPayload("爸爸", "BIRTHDAY", "LUNAR", 8, 3, "PRIVATE", null));

        assertFalse(day.get("nextDate").isNull());
        assertTrue(day.get("daysUntil").asInt() >= 0);
        // 文本由换算核心生成,与生日簿同一格式(月名+日数值,如「农历8月3日」)
        assertTrue(day.get("nextDateLunarText").asText().startsWith("农历"), day.get("nextDateLunarText").asText());
    }

    @Test
    void 在一起的日子无月日_服务端取当天为起始日() {
        String token = login();
        LocalDate today = LocalDate.now();
        JsonNode day = post("/api/days", token, dayPayload("老伴", "TOGETHER", "SOLAR", null, null, "PRIVATE", null));

        assertEquals("SOLAR", day.get("calendarType").asText());
        assertEquals(today.getMonthValue(), day.get("month").asInt());
        assertEquals(today.getDayOfMonth(), day.get("day").asInt());
        assertEquals(1, day.get("togetherDays").asInt());
        assertTrue(day.get("nextDate").isNull());
        assertTrue(day.get("daysUntil").isNull());
        assertTrue(day.get("nextDateLunarText").isNull());
    }

    @Test
    void 月日越界与公历二月三十被拒() {
        String token = login();
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR", 13, 1, "PRIVATE", null)), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR", 1, 32, "PRIVATE", null)), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR", 2, 30, "PRIVATE", null)), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "LUNAR", 8, 31, "PRIVATE", null)), "40000");
    }

    @Test
    void 在一起的日子传农历被拒() {
        String token = login();
        assertErrorCode(post("/api/days", token, dayPayload("老伴", "TOGETHER", "LUNAR", null, null, "PRIVATE", null)), "40000");
    }

    @Test
    void 农历三十全窗口无落点被拒() {
        String token = login();
        // 扫描当前试算窗口(today-1 ~ today+2 年)内「农历三十」全不中的月份;存在则验证 40000
        int year = LocalDate.now().getYear();
        Integer noLandingMonth = null;
        for (int month = 1; month <= 12 && noLandingMonth == null; month++) {
            boolean anyLanding = false;
            for (int candidateYear = year - 1; candidateYear <= year + 2; candidateYear++) {
                if (CalendarConvertCore.lunarCandidate(candidateYear, month, 30, false) != null) {
                    anyLanding = true;
                    break;
                }
            }
            if (!anyLanding) {
                noLandingMonth = month;
            }
        }
        if (noLandingMonth == null) {
            return; // 窗口内所有农历月都存在三十,该拒绝分支当日不可触发
        }
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "LUNAR",
                noLandingMonth, 30, "PRIVATE", null)), "40000");
    }

    @Test
    void 非法提醒被拒_合法提醒创建后回显() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(7, 7))), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(1))), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(7, 3, 0, 7))), "40000");

        JsonNode day = post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(0, 3, 7)));
        assertEquals(3, day.get("reminders").size());
        assertEquals(7, day.get("reminders").get(0).asInt());
        assertEquals(3, day.get("reminders").get(1).asInt());
        assertEquals(0, day.get("reminders").get(2).asInt());
    }

    @Test
    void 无家族用户传FAMILY被降级为PRIVATE() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        JsonNode day = post("/api/days", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "FAMILY", null));
        assertEquals("PRIVATE", day.get("visibility").asText());
    }

    @Test
    void 称呼与类型非法被拒() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        assertErrorCode(post("/api/days", token, dayPayload("", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null)), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("一二三四五六七八九十abcde", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null)), "40000");
        assertErrorCode(post("/api/days", token, dayPayload("妈妈", "FOO", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null)), "40000");
    }

    // ---- GET/PUT/DELETE /api/days/{id} ----

    @Test
    void 详情按可见性过滤_家人看他人PRIVATE等价不存在() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        int[] plus7 = monthDayAfter(7);
        long ownerPrivate = createDay(owner, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));
        long memberFamily = createDay(member, dayPayload("亲家", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "FAMILY", null));

        assertEquals(ownerPrivate, get("/api/days/" + ownerPrivate, owner).get("id").asLong());
        assertErrorCode(get("/api/days/" + ownerPrivate, member), "40400");
        assertEquals(memberFamily, get("/api/days/" + memberFamily, owner).get("id").asLong());
    }

    @Test
    void 创建人编辑全量覆盖且提醒配置不受影响() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        long id = createDay(token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(7)));

        JsonNode updated = put("/api/days/" + id, token, dayPayload("老伴", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));
        assertEquals("老伴", updated.get("relation").asText());
        assertEquals("👴", updated.get("emoji").asText());
        assertEquals(1, updated.get("reminders").size(), "PUT 不携带 reminders,配置不丢失");
        assertEquals(7, updated.get("reminders").get(0).asInt());
    }

    @Test
    void 户主可编辑删除他人FAMILY_普通成员越权被拒() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        String member2 = family[2];
        int[] plus7 = monthDayAfter(7);
        long id = createDay(member, dayPayload("亲家", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "FAMILY", null));

        JsonNode updated = put("/api/days/" + id, owner, dayPayload("亲家公", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "FAMILY", null));
        assertEquals("亲家公", updated.get("relation").asText());
        assertErrorCode(put("/api/days/" + id, member2, dayPayload("亲家母", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "FAMILY", null)), "40400");
        assertErrorCode(delete("/api/days/" + id, member2), "40400");
        assertEquals(true, delete("/api/days/" + id, owner).get("deleted").asBoolean());
    }

    @Test
    void 删除后详情不可见且到期计算不再产出() {
        String token = login();
        int[] plus0 = monthDayAfter(0);
        long id = createDay(token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus0[0], plus0[1], "PRIVATE", List.of(0)));

        JsonNode dueBefore = get("/api/days/reminder-due?date=" + LocalDate.now(), token);
        assertTrue(containsDueItem(dueBefore.get("items"), id), "删除前该日子应命中当天提醒");
        assertEquals(true, delete("/api/days/" + id, token).get("deleted").asBoolean());
        assertErrorCode(get("/api/days/" + id, token), "40400");
        JsonNode dueAfter = get("/api/days/reminder-due?date=" + LocalDate.now(), token);
        assertFalse(containsDueItem(dueAfter.get("items"), id), "day_reminder 行随日子清除,到期计算不再产出");
    }

    private boolean containsDueItem(JsonNode items, long dayId) {
        for (JsonNode item : items) {
            if (item.get("dayId").asLong() == dayId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void 不存在的日子对增删改查都等价不存在() {
        String token = login();
        assertErrorCode(get("/api/days/999999", token), "40400");
        assertErrorCode(put("/api/days/999999", token, dayPayload("妈妈", "BIRTHDAY", "SOLAR", 5, 20, "PRIVATE", null)), "40400");
        assertErrorCode(delete("/api/days/999999", token), "40400");
    }

    // ---- PUT /api/days/{id}/reminder ----

    @Test
    void 提醒设置幂等覆盖_空数组关闭() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        long id = createDay(token, dayPayload("妈妈", "BIRTHDAY", "SOLAR", plus7[0], plus7[1], "PRIVATE", null));

        JsonNode set = put("/api/days/" + id + "/reminder", token, Map.of("reminders", List.of(7, 0)));
        assertEquals(id, set.get("dayId").asLong());
        assertEquals(2, set.get("reminders").size());

        JsonNode overwrite = put("/api/days/" + id + "/reminder", token, Map.of("reminders", List.of(3)));
        assertEquals(1, overwrite.get("reminders").size());
        assertEquals(3, overwrite.get("reminders").get(0).asInt());

        JsonNode off = put("/api/days/" + id + "/reminder", token, Map.of("reminders", List.of()));
        assertEquals(0, off.get("reminders").size());
    }

    @Test
    void 提醒越权与非法值被拒() {
        String[] family = family();
        String owner = family[0];
        String member = family[1];
        int[] plus7 = monthDayAfter(7);
        long ownerPrivate = createDay(owner, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));

        assertErrorCode(put("/api/days/" + ownerPrivate + "/reminder", member, Map.of("reminders", List.of(7))), "40400");
        assertErrorCode(put("/api/days/" + ownerPrivate + "/reminder", owner, Map.of("reminders", List.of(5))), "40000");
    }

    // ---- GET /api/days/reminder-due ----

    @Test
    void 到期计算命中7天3天当天与相伴日() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        long day7 = createDay(token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", List.of(7)));
        int[] plus3 = monthDayAfter(3);
        long day3 = createDay(token, dayPayload("结婚", "ANNIVERSARY", "SOLAR",
                plus3[0], plus3[1], "PRIVATE", List.of(3)));
        int[] plus0 = monthDayAfter(0);
        long day0 = createDay(token, dayPayload("孙女", "BIRTHDAY", "SOLAR",
                plus0[0], plus0[1], "PRIVATE", List.of(0)));
        long together = createDay(token, dayPayload("老伴", "TOGETHER", "SOLAR", null, null, "PRIVATE", List.of(0)));

        JsonNode due = get("/api/days/reminder-due?date=" + LocalDate.now(), token);
        assertTrue(due.get("total").asInt() >= 4, "本用例 4 条命中必须全在(共享测试库,可能有他人配置)");
        JsonNode items = due.get("items");
        JsonNode item7 = findItem(items, day7);
        assertEquals(7, item7.get("remindOffset").asInt());
        assertEquals(7, item7.get("daysTo").asInt());
        assertEquals("妈妈的生日", item7.get("title").asText());
        assertFalse(item7.get("actionText").asText().isBlank());
        assertTrue(item7.get("daysSince").isNull());

        JsonNode item3 = findItem(items, day3);
        assertEquals(3, item3.get("remindOffset").asInt());
        assertEquals(3, item3.get("daysTo").asInt());
        assertEquals("结婚的纪念日", item3.get("title").asText());

        JsonNode item0 = findItem(items, day0);
        assertEquals(0, item0.get("remindOffset").asInt());
        assertEquals(0, item0.get("daysTo").asInt());

        JsonNode itemTogether = findItem(items, together);
        assertEquals(0, itemTogether.get("remindOffset").asInt());
        assertTrue(itemTogether.get("daysTo").isNull());
        assertEquals(1, itemTogether.get("daysSince").asInt());
    }

    private JsonNode findItem(JsonNode items, long dayId) {
        for (JsonNode item : items) {
            if (item.get("dayId").asLong() == dayId) {
                return item;
            }
        }
        throw new AssertionError("到期清单中未找到日子 " + dayId + ": " + items);
    }

    @Test
    void 到期计算缺省按当天_非法日期与无配置不产出() {
        String token = login();
        int[] plus0 = monthDayAfter(0);
        long withReminder = createDay(token, dayPayload("妈妈", "BIRTHDAY", "SOLAR",
                plus0[0], plus0[1], "PRIVATE", List.of(0)));
        int[] plus7 = monthDayAfter(7);
        long withoutReminder = createDay(token, dayPayload("儿子", "BIRTHDAY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));

        JsonNode items = get("/api/days/reminder-due", token).get("items");
        assertTrue(containsDueItem(items, withReminder), "缺省 date=当天");
        assertFalse(containsDueItem(items, withoutReminder), "无提醒配置不产出");
        assertErrorCode(get("/api/days/reminder-due?date=2026/10/10", token), "40000");
    }

    @Test
    void 到期计算未登录被拒() {
        assertErrorCode(get("/api/days/reminder-due", null), "40100");
    }

    // ---- v1.8.0 year 扩展与周年称谓(设计 v2.2 §3 用例 20-23) ----

    @Test
    void 纪念日补year_周年称谓与标签输出() {
        String token = login();
        int[] plus0 = monthDayAfter(0);
        int thisYear = LocalDate.now().getYear();
        JsonNode day = post("/api/days", token, yearPayload("结婚", "ANNIVERSARY", plus0[0], plus0[1],
                thisYear - 40));

        assertEquals(thisYear - 40, day.get("year").asInt());
        assertEquals(40, day.get("anniversaryNumber").asInt(), "N=nextDate 年-year");
        assertEquals("红宝石婚", day.get("anniversaryTitle").asText());
        assertEquals("结婚 40 周年 · 红宝石婚", day.get("anniversaryLabel").asText());

        // 未收录档位(16 周年):title 为 null,label 仍输出「N 周年」
        JsonNode day16 = post("/api/days", token, yearPayload("结婚", "ANNIVERSARY", plus0[0], plus0[1],
                thisYear - 16));
        assertEquals(16, day16.get("anniversaryNumber").asInt());
        assertTrue(day16.get("anniversaryTitle").isNull(), "16 周年未收录为 null");
        assertEquals("结婚 16 周年", day16.get("anniversaryLabel").asText());
    }

    @Test
    void 纪念日year缺省_周年字段全为null() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        JsonNode day = post("/api/days", token, dayPayload("结婚", "ANNIVERSARY", "SOLAR",
                plus7[0], plus7[1], "PRIVATE", null));
        assertTrue(day.get("year").isNull());
        assertTrue(day.get("anniversaryNumber").isNull());
        assertTrue(day.get("anniversaryTitle").isNull());
        assertTrue(day.get("anniversaryLabel").isNull());
    }

    @Test
    void 生日带year仅存档_不输出任何年龄派生字段() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        JsonNode day = post("/api/days", token, yearPayload("妈妈", "BIRTHDAY", plus7[0], plus7[1], 1950));

        assertEquals(1950, day.get("year").asInt(), "year 原值回显");
        assertTrue(day.get("anniversaryNumber").isNull());
        assertTrue(day.get("anniversaryTitle").isNull());
        assertTrue(day.get("anniversaryLabel").isNull());
        // 隐私红线:响应中不得出现任何年龄类键
        day.fieldNames().forEachRemaining(field -> assertTrue(
                !field.toLowerCase().contains("age"), "出现年龄派生键: " + field));
    }

    @Test
    void 在一起补year_togetherDays按补录年起算() {
        String token = login();
        int thisYear = LocalDate.now().getYear();
        JsonNode day = post("/api/days", token, yearPayload("老伴", "TOGETHER", 6, 15, thisYear - 7));

        long expected = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(thisYear - 7, 6, 15), LocalDate.now());
        assertEquals(expected, day.get("togetherDays").asLong(), "起始日=year+month/day,早于 createdAt 以此为准");
        assertTrue(day.get("togetherDays").asLong() > 1000, "不能用 createdAt 年起算");
    }

    @Test
    void year越界被拒() {
        String token = login();
        int[] plus7 = monthDayAfter(7);
        assertErrorCode(post("/api/days", token, yearPayload("妈妈", "BIRTHDAY",
                plus7[0], plus7[1], 1899)), "40000");
        assertErrorCode(post("/api/days", token, yearPayload("妈妈", "BIRTHDAY",
                plus7[0], plus7[1], LocalDate.now().getYear() + 1)), "40000");
    }

    @Test
    void PUT全量覆盖year_周年字段即时重算() {
        String token = login();
        int[] plus0 = monthDayAfter(0);
        int thisYear = LocalDate.now().getYear();
        long id = createDay(token, yearPayload("结婚", "ANNIVERSARY", plus0[0], plus0[1], thisYear - 40));
        assertEquals(40, get("/api/days/" + id, token).get("anniversaryNumber").asInt());

        // 改 year → 字段即时重算
        JsonNode updated = put("/api/days/" + id, token,
                yearPayload("结婚", "ANNIVERSARY", plus0[0], plus0[1], thisYear - 50));
        assertEquals(50, updated.get("anniversaryNumber").asInt());
        assertEquals("金婚", updated.get("anniversaryTitle").asText());

        // PUT 不带 year → 全量覆盖清空,周年字段回 null
        JsonNode cleared = put("/api/days/" + id, token, dayPayload("结婚", "ANNIVERSARY", "SOLAR",
                plus0[0], plus0[1], "PRIVATE", null));
        assertTrue(cleared.get("year").isNull());
        assertTrue(cleared.get("anniversaryNumber").isNull());
    }

    @Test
    void 在一起year配平年二月二十九被拒_闰年放行() {
        String token = login();
        assertErrorCode(post("/api/days", token, yearPayload("老伴", "TOGETHER", 2, 29, 2019)), "40000");
        JsonNode leap = post("/api/days", token, yearPayload("老伴", "TOGETHER", 2, 29, 2024));
        assertEquals(2024, leap.get("year").asInt());
        long expected = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(2024, 2, 29), LocalDate.now());
        assertEquals(expected, leap.get("togetherDays").asLong());
    }
}
