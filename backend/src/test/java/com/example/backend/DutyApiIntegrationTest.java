package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * 值班日历集成测试(H2 内存库):月历/今日状态卡/单日编辑/规则批量应用/提醒配置。
 * 用例矩阵对齐 docs/design/v2.3-duty-design.md §3;
 * today 用 ?date= 自测参数注入时钟,规则用固定 2026-11 起始日期断言序列。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DutyApiIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();

    /** 2026-11-01 是星期日;做一休一序列从该日起:早,休,早,休... */
    private static final String NOV = "2026-11";

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return post("/api/auth/login", null, Map.of("code", "dev-duty-" + userCounter.incrementAndGet()))
                .get("token").asText();
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

    private JsonNode setShift(String token, String date, String shift) {
        return post("/api/duty/shifts", token, Map.of("date", date, "shift", shift));
    }

    private JsonNode applyRule(String token, String ruleType, String startDate, String endDate) {
        return post("/api/duty/rules/apply", token,
                Map.of("ruleType", ruleType, "startDate", startDate, "endDate", endDate));
    }

    private JsonNode calendar(String token, String month) {
        return get("/api/duty/calendar?month=" + month, token);
    }

    private JsonNode today(String token, String date) {
        return get("/api/duty/today?date=" + date, token);
    }

    /** 取 shifts 数组中指定日期的 shift 值;无该日返回 null */
    private String shiftOf(JsonNode calendar, String date) {
        for (JsonNode item : calendar.get("shifts")) {
            if (date.equals(item.get("date").asText())) {
                return item.get("shift").asText();
            }
        }
        return null;
    }

    // ---- GET /api/duty/calendar ----

    @Test
    void 月历空月_空shifts零统计() {
        String token = login();
        JsonNode resp = calendar(token, NOV);
        assertEquals(NOV, resp.get("month").asText());
        assertEquals(0, resp.get("shifts").size());
        JsonNode stats = resp.get("stats");
        assertEquals(0, stats.get("early").asInt());
        assertEquals(0, stats.get("mid").asInt());
        assertEquals(0, stats.get("night").asInt());
        assertEquals(0, stats.get("rest").asInt());
    }

    @Test
    void 月历统计与升序_单日设置后可查() {
        String token = login();
        setShift(token, "2026-11-03", "early");
        setShift(token, "2026-11-01", "night");
        setShift(token, "2026-11-05", "rest");
        setShift(token, "2026-11-04", "mid");
        JsonNode resp = calendar(token, NOV);
        assertEquals(4, resp.get("shifts").size());
        assertEquals("2026-11-01", resp.get("shifts").get(0).get("date").asText());
        assertEquals("night", shiftOf(resp, "2026-11-01"));
        assertEquals("early", shiftOf(resp, "2026-11-03"));
        assertEquals("mid", shiftOf(resp, "2026-11-04"));
        assertEquals("rest", shiftOf(resp, "2026-11-05"));
        JsonNode stats = resp.get("stats");
        assertEquals(1, stats.get("early").asInt());
        assertEquals(1, stats.get("mid").asInt());
        assertEquals(1, stats.get("night").asInt());
        assertEquals(1, stats.get("rest").asInt());
    }

    @Test
    void 月历month缺省当月_非法格式40000() {
        String token = login();
        assertEquals(LocalDate.now().toString().substring(0, 7),
                get("/api/duty/calendar", token).get("month").asText());
        assertErrorCode(calendar(token, "202611"), "40000");
        assertErrorCode(calendar(token, "2026-13"), "40000");
    }

    // ---- GET /api/duty/today ----

    @Test
    void 今日无排班_todayShift为null_nextDuty为null() {
        String token = login();
        JsonNode resp = today(token, "2026-11-10");
        assertEquals("2026-11-10", resp.get("date").asText());
        assertTrue(resp.get("todayShift").isNull());
        assertTrue(resp.get("nextDuty").isNull());
        assertEquals("advance_1d", resp.get("remind").get("timing").asText());
        assertFalse(resp.get("remind").get("enabled").asBoolean());
    }

    @Test
    void 今日班次与下次值班_今天不参与倒计时() {
        String token = login();
        setShift(token, "2026-11-10", "early");      // 今天
        setShift(token, "2026-11-11", "rest");        // 明天=休,跳过
        setShift(token, "2026-11-12", "night");       // 后天=下次值班
        JsonNode resp = today(token, "2026-11-10");
        assertEquals("early", resp.get("todayShift").asText());
        JsonNode next = resp.get("nextDuty");
        assertEquals("2026-11-12", next.get("date").asText());
        assertEquals("night", next.get("shift").asText());
        assertEquals(2, next.get("daysUntil").asInt());
    }

    @Test
    void 下次值班60天窗口_窗口外不计_休班排除() {
        String token = login();
        LocalDate base = LocalDate.of(2026, 11, 10);
        // 60 天整=2027-01-09 计入;61 天=2027-01-10 不计入;中间全是休
        for (int i = 1; i <= 59; i++) {
            setShift(token, base.plusDays(i).toString(), "rest");
        }
        setShift(token, base.plusDays(60).toString(), "early");  // 窗口内最后一天
        setShift(token, base.plusDays(61).toString(), "mid");    // 窗口外
        JsonNode resp = today(token, "2026-11-10");
        JsonNode next = resp.get("nextDuty");
        assertEquals(base.plusDays(60).toString(), next.get("date").asText());
        assertEquals(60, next.get("daysUntil").asInt());
    }

    @Test
    void today日期非法40000() {
        String token = login();
        assertErrorCode(today(token, "2026/11/10"), "40000");
    }

    // ---- POST /api/duty/shifts ----

    @Test
    void 单日设置upsert_覆盖为manual() {
        String token = login();
        JsonNode first = setShift(token, "2026-11-20", "early");
        assertEquals("early", first.get("shift").asText());
        assertEquals("manual", first.get("source").asText());
        JsonNode second = setShift(token, "2026-11-20", "night");
        assertEquals("night", second.get("shift").asText());
        assertEquals(1, calendar(token, NOV).get("shifts").size());
    }

    @Test
    void 单日设置覆盖规则来源_删除单日后重新应用回填() {
        String token = login();
        applyRule(token, "on1off1", "2026-11-01", "2026-11-10");
        // 手动覆盖第 2 天(原本是休)
        JsonNode manual = setShift(token, "2026-11-02", "mid");
        assertEquals("manual", manual.get("source").asText());
        // 清除该日:仅删该行,规则本体不动
        JsonNode cleared = delete("/api/duty/shifts/2026-11-02", token);
        assertTrue(cleared.get("cleared").asBoolean());
        assertNull(shiftOf(calendar(token, NOV), "2026-11-02"));
        // 重新应用规则回填
        applyRule(token, "on1off1", "2026-11-01", "2026-11-10");
        assertEquals("rest", shiftOf(calendar(token, NOV), "2026-11-02"));
    }

    @Test
    void 单日设置shift越值域40000_日期非法40000_请求体缺失40000() {
        String token = login();
        assertErrorCode(setShift(token, "2026-11-20", "earlye"), "40000");
        assertErrorCode(setShift(token, "11月20日", "early"), "40000");
        assertErrorCode(post("/api/duty/shifts", token, Map.of()), "40000");
    }

    // ---- DELETE /api/duty/shifts/{date} ----

    @Test
    void 清除幂等_无排班也成功() {
        String token = login();
        JsonNode resp = delete("/api/duty/shifts/2026-12-25", token);
        assertTrue(resp.get("cleared").asBoolean());
        assertEquals("2026-12-25", resp.get("date").asText());
    }

    @Test
    void 清除日期非法40000() {
        String token = login();
        assertErrorCode(delete("/api/duty/shifts/not-a-date", token), "40000");
    }

    // ---- DELETE /api/duty/shifts?from=&to=(范围清除/批量撤销) ----

    @Test
    void 范围清除_删除段内全部排班_返回实际行数() {
        String token = login();
        applyRule(token, "on1off1", "2026-11-01", "2026-11-10");  // 段内 10 行
        setShift(token, "2026-11-20", "night");                   // 段外 1 行
        JsonNode resp = delete("/api/duty/shifts?from=2026-11-01&to=2026-11-10", token);
        assertEquals(10, resp.get("clearedDays").asInt());
        assertEquals("2026-11-10", resp.get("to").asText());
        JsonNode cal = calendar(token, NOV);
        assertNull(shiftOf(cal, "2026-11-01"));
        assertEquals("night", shiftOf(cal, "2026-11-20"));  // 段外不动
        assertEquals(1, cal.get("shifts").size());
    }

    @Test
    void 范围清除幂等_段内无排班返回0() {
        String token = login();
        JsonNode resp = delete("/api/duty/shifts?from=2026-12-01&to=2026-12-31", token);
        assertEquals(0, resp.get("clearedDays").asInt());
        assertEquals("2026-12-01", resp.get("from").asText());
    }

    @Test
    void 范围清除不触碰规则本体_重新应用回填() {
        String token = login();
        applyRule(token, "daily", "2026-11-01", "2026-11-05");
        delete("/api/duty/shifts?from=2026-11-01&to=2026-11-05", token);
        // 规则行仍在
        JsonNode rule = get("/api/duty/rule", token);
        assertTrue(rule.get("active").asBoolean());
        assertEquals("daily", rule.get("ruleType").asText());
        // 重新应用回填
        applyRule(token, "daily", "2026-11-01", "2026-11-05");
        assertEquals(5, calendar(token, NOV).get("shifts").size());
    }

    @Test
    void 范围清除参数错误_倒序_非法日期_缺参_未登录() {
        String token = login();
        assertErrorCode(delete("/api/duty/shifts?from=2026-11-10&to=2026-11-01", token), "40000");
        assertErrorCode(delete("/api/duty/shifts?from=Nov-1&to=2026-11-10", token), "40000");
        assertErrorCode(delete("/api/duty/shifts?from=2026-11-01", token), "40000");
        assertErrorCode(delete("/api/duty/shifts?from=2026-11-01&to=2026-11-10", null), "40100");
    }

    @Test
    void 范围清除用户隔离() {
        String tokenA = login();
        String tokenB = login();
        applyRule(tokenA, "daily", "2026-11-01", "2026-11-05");
        // B 清同样范围,A 的排班不受影响
        delete("/api/duty/shifts?from=2026-11-01&to=2026-11-05", tokenB);
        assertEquals(5, calendar(tokenA, NOV).get("shifts").size());
    }

    // ---- GET /api/duty/rule ----

    @Test
    void 无规则空壳_应用后回显() {
        String token = login();
        JsonNode empty = get("/api/duty/rule", token);
        assertFalse(empty.get("active").asBoolean());
        assertEquals(0, empty.get("appliedDays").asInt());
        assertTrue(empty.get("ruleType").isNull());

        applyRule(token, "on2off2", "2026-11-01", "2026-11-08");
        JsonNode rule = get("/api/duty/rule", token);
        assertTrue(rule.get("active").asBoolean());
        assertEquals("on2off2", rule.get("ruleType").asText());
        assertEquals("2026-11-01", rule.get("startDate").asText());
        assertEquals("2026-11-08", rule.get("endDate").asText());
        assertEquals(8, rule.get("appliedDays").asInt());
    }

    // ---- POST /api/duty/rules/apply ----

    @Test
    void 做一休一序列_早休交替填充() {
        String token = login();
        JsonNode resp = applyRule(token, "on1off1", "2026-11-01", "2026-11-06");
        assertEquals(6, resp.get("appliedDays").asInt());
        assertFalse(resp.get("truncated").asBoolean());
        JsonNode cal = calendar(token, NOV);
        assertEquals("early", shiftOf(cal, "2026-11-01"));
        assertEquals("rest", shiftOf(cal, "2026-11-02"));
        assertEquals("early", shiftOf(cal, "2026-11-03"));
        assertEquals("rest", shiftOf(cal, "2026-11-04"));
        assertEquals("early", shiftOf(cal, "2026-11-05"));
        assertEquals("rest", shiftOf(cal, "2026-11-06"));
        assertNull(shiftOf(cal, "2026-11-07"));
        // source 记 rule:on1off1
        assertEquals("rule:on1off1", cal.get("shifts").get(0).get("source").asText());
    }

    @Test
    void 做二休二与四班三运转序列() {
        String token = login();
        applyRule(token, "on2off2", "2026-11-01", "2026-11-08");
        JsonNode cal = calendar(token, NOV);
        assertEquals("early", shiftOf(cal, "2026-11-01"));
        assertEquals("early", shiftOf(cal, "2026-11-02"));
        assertEquals("rest", shiftOf(cal, "2026-11-03"));
        assertEquals("rest", shiftOf(cal, "2026-11-04"));

        String token2 = login();
        applyRule(token2, "emnr", "2026-11-01", "2026-11-08");
        JsonNode cal2 = calendar(token2, NOV);
        assertEquals("early", shiftOf(cal2, "2026-11-01"));
        assertEquals("mid", shiftOf(cal2, "2026-11-02"));
        assertEquals("night", shiftOf(cal2, "2026-11-03"));
        assertEquals("rest", shiftOf(cal2, "2026-11-04"));
        assertEquals("early", shiftOf(cal2, "2026-11-05"));
        JsonNode stats = cal2.get("stats");
        assertEquals(2, stats.get("early").asInt());
        assertEquals(2, stats.get("mid").asInt());
        assertEquals(2, stats.get("night").asInt());
        assertEquals(2, stats.get("rest").asInt());
    }

    @Test
    void 每周五值班_只填星期五() {
        String token = login();
        // 2026-11-06 与 2026-11-13 是星期五;先在 11-04(周三)造一条已有排班,验证 weekday 不动它
        setShift(token, "2026-11-04", "night");
        JsonNode resp = applyRule(token, "weekday", "2026-11-01", "2026-11-15");
        assertEquals(2, resp.get("appliedDays").asInt());
        JsonNode cal = calendar(token, NOV);
        assertEquals("early", shiftOf(cal, "2026-11-06"));
        assertEquals("early", shiftOf(cal, "2026-11-13"));
        assertEquals(DayOfWeek.FRIDAY, LocalDate.of(2026, 11, 6).getDayOfWeek());
        assertEquals(DayOfWeek.FRIDAY, LocalDate.of(2026, 11, 13).getDayOfWeek());
        // 非周五的已有排班保持原状
        assertEquals("night", shiftOf(cal, "2026-11-04"));
        assertNull(shiftOf(cal, "2026-11-02"));
        assertNull(shiftOf(cal, "2026-11-11"));
    }

    @Test
    void 规则覆盖已有排班_不论来源() {
        String token = login();
        setShift(token, "2026-11-01", "night");  // manual 来源也会被覆盖
        applyRule(token, "daily", "2026-11-01", "2026-11-03");
        JsonNode cal = calendar(token, NOV);
        assertEquals("early", shiftOf(cal, "2026-11-01"));
        assertEquals("early", shiftOf(cal, "2026-11-02"));
        assertEquals("early", shiftOf(cal, "2026-11-03"));
    }

    @Test
    void 规则重新应用整行覆盖_规则行唯一() {
        String token = login();
        applyRule(token, "daily", "2026-11-01", "2026-11-03");
        JsonNode second = applyRule(token, "on1off1", "2026-12-01", "2026-12-10");
        assertEquals(10, second.get("appliedDays").asInt());
        JsonNode rule = get("/api/duty/rule", token);
        assertEquals("on1off1", rule.get("ruleType").asText());
        assertEquals("2026-12-10", rule.get("endDate").asText());
        // 11 月的旧排班不因重新应用被清除(规则只作用于新范围)
        assertEquals(3, calendar(token, NOV).get("shifts").size());
    }

    @Test
    void 跨度超365天截断_truncated标记() {
        String token = login();
        JsonNode resp = applyRule(token, "daily", "2026-11-01", "2028-11-01");
        assertTrue(resp.get("truncated").asBoolean());
        assertEquals(365, resp.get("appliedDays").asInt());
        assertEquals("2027-10-31", resp.get("endDate").asText());  // 2026-11-01 + 364 天
    }

    @Test
    void 规则应用参数错误_类型越值域_日期非法_倒序范围() {
        String token = login();
        assertErrorCode(applyRule(token, "on3off3", "2026-11-01", "2026-11-10"), "40000");
        assertErrorCode(applyRule(token, "daily", "Nov-1", "2026-11-10"), "40000");
        assertErrorCode(applyRule(token, "daily", "2026-11-10", "2026-11-01"), "40000");
        assertErrorCode(post("/api/duty/rules/apply", token, Map.of()), "40000");
    }

    // ---- PUT /api/duty/remind-config ----

    @Test
    void 提醒配置保存回显_再保存覆盖() {
        String token = login();
        JsonNode first = put("/api/duty/remind-config", token,
                Map.of("timing", "advance_2h", "enabled", true));
        assertEquals("advance_2h", first.get("timing").asText());
        assertTrue(first.get("enabled").asBoolean());

        JsonNode second = put("/api/duty/remind-config", token,
                Map.of("timing", "same_day_08", "enabled", false));
        assertEquals("same_day_08", second.get("timing").asText());
        assertFalse(second.get("enabled").asBoolean());

        // today 联动回显
        JsonNode resp = today(token, "2026-11-10");
        assertEquals("same_day_08", resp.get("remind").get("timing").asText());
        assertFalse(resp.get("remind").get("enabled").asBoolean());
    }

    @Test
    void 提醒时机越值域40000() {
        String token = login();
        assertErrorCode(put("/api/duty/remind-config", token,
                Map.of("timing", "advance_3h", "enabled", true)), "40000");
        assertErrorCode(put("/api/duty/remind-config", token, Map.of()), "40000");
    }

    // ---- 鉴权 ----

    @Test
    void 未登录40100_七端点全量() {
        assertErrorCode(get("/api/duty/calendar", null), "40100");
        assertErrorCode(get("/api/duty/today", null), "40100");
        assertErrorCode(post("/api/duty/shifts", null, Map.of("date", "2026-11-01", "shift", "early")), "40100");
        assertErrorCode(delete("/api/duty/shifts/2026-11-01", null), "40100");
        assertErrorCode(get("/api/duty/rule", null), "40100");
        assertErrorCode(post("/api/duty/rules/apply", null,
                Map.of("ruleType", "daily", "startDate", "2026-11-01", "endDate", "2026-11-30")), "40100");
        assertErrorCode(put("/api/duty/remind-config", null, Map.of("timing", "advance_1d", "enabled", true)), "40100");
    }

    // ---- 用户隔离 ----

    @Test
    void 值班数据用户隔离_互相不可见() {
        String tokenA = login();
        String tokenB = login();
        setShift(tokenA, "2026-11-15", "early");
        assertEquals(0, calendar(tokenB, NOV).get("shifts").size());
        JsonNode respB = today(tokenB, "2026-11-15");
        assertTrue(respB.get("todayShift").isNull());
        assertTrue(respB.get("nextDuty").isNull());
        // B 的清除不影响 A
        delete("/api/duty/shifts/2026-11-15", tokenB);
        JsonNode calA = calendar(tokenA, NOV);
        assertEquals("early", shiftOf(calA, "2026-11-15"));
    }
}
