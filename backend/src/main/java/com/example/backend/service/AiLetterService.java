package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.CardDtos.LetterGenerateResponse;
import com.example.backend.dto.CardDtos.LetterSegment;
import com.example.backend.dto.CardDtos.LetterSegmentRole;
import com.example.backend.dto.CardDtos.LetterSource;
import com.example.backend.entity.LetterGenerateQuotaEntity;
import com.example.backend.repository.LetterGenerateQuotaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 感恩信 AI 生成(OpenAI 兼容网关,Java 21 内置 HttpClient,零第三方 SDK)。
 * 三层防线:prompt 负面词约束 → 逐 variant 违禁词过滤+降级写法补位 → 整体解析失败全量 FALLBACK。
 * 任何 AI 异常路径都不向上抛(信件功能永不因 AI 不可用而报错);日志只打耗时与状态码,不打请求体(隐私)。
 * 频控:每日 5 次/人,REQUIRES_NEW 表 upsert 独立计数(后续 40300/AI 异常降级均不回滚)。
 */
@Service
public class AiLetterService {

    private static final Logger log = LoggerFactory.getLogger(AiLetterService.class);

    /** 每日每人生成上限(契约 40300:AI 与降级路径统一计数) */
    public static final int DAILY_LIMIT = 5;

    private static final int MAX_AI_VARIANT_LENGTH = 120;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** 单例客户端:连接 2s/每请求 10s/不跟随重定向/不重试(超时即降级) */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper mapper;
    private final LetterGenerateQuotaRepository quotaRepository;

    @Value("${ai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:gpt-4o-mini}")
    private String model;

    public AiLetterService(ObjectMapper mapper, LetterGenerateQuotaRepository quotaRepository) {
        this.mapper = mapper;
        this.quotaRepository = quotaRepository;
    }

    /** 单测注入网关配置(生产走环境变量→properties) */
    void setConfig(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    // ---- 频控(REQUIRES_NEW:调 generate 即计数,后续异常不回滚) ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeQuota(Long userId) {
        LocalDate today = LocalDate.now();
        LetterGenerateQuotaEntity quota = quotaRepository.findByUserIdAndQuotaDate(userId, today).orElse(null);
        if (quota != null) {
            if (quota.getCount() >= DAILY_LIMIT) {
                throw ApiException.rateLimited("今天 " + DAILY_LIMIT + " 次写信用完了，明天再来");
            }
            quotaRepository.incrementCount(userId, today);
            return;
        }
        // 首次:INSERT,撞唯一约束(并发)则回退原子自增
        try {
            quotaRepository.saveAndFlush(new LetterGenerateQuotaEntity(userId, today, 1));
        } catch (DataIntegrityViolationException e) {
            quotaRepository.incrementCount(userId, today);
        }
    }

    // ---- 生成主链路 ----

    /** AI 优先,任何异常静默降级 FALLBACK;永不向上抛 AI 相关异常 */
    public LetterGenerateResponse generate(String recipient, List<String> stories, String tone) {
        if (apiKey == null || apiKey.isBlank()) {
            return LetterTextLib.assemble(recipient, stories, tone);
        }
        try {
            long start = System.currentTimeMillis();
            String content = callGateway(buildRequestBody(recipient, stories, tone));
            LetterGenerateResponse response = parseAiContent(content, recipient, stories, tone);
            log.info("感恩信 AI 生成成功: 耗时={}ms, 段数={}",
                    System.currentTimeMillis() - start, response.getParagraphs().size());
            return response;
        } catch (Exception e) {
            // 只记异常概要,不打请求体(收信人称呼属用户输入,隐私最小化)
            log.warn("感恩信 AI 生成失败,降级 FALLBACK: {}", e.getMessage());
            return LetterTextLib.assemble(recipient, stories, tone);
        }
    }

    /** 组装 chat/completions 请求体(手写 JSON 结构,不走 DTO) */
    private String buildRequestBody(String recipient, List<String> stories, String tone) throws Exception {
        String negativeWords = String.join("、", DayTextLib.FORBIDDEN_WORDS);
        String system = "你是一位中文家书代笔人。请严格遵守："
                + "1.只输出严格 JSON（不要 markdown 代码块包裹）；"
                + "2.输出结构为 {\"opening\":[\"写法一\",\"写法二\"],"
                + "\"stories\":[{\"key\":\"故事原文\",\"variants\":[\"写法一\",\"写法二\"]}],"
                + "\"closing\":[\"写法一\",\"写法二\"]}；"
                + "3.stories 数组必须与用户给出的故事一一对应（顺序一致、key 逐字相同、数量相同）；"
                + "4.每段每条写法不超过 80 字，温暖口语化，以「您」称呼收信人；"
                + "5.每段恰好给出 2 条写法；"
                + "6.不得出现以下词汇：" + negativeWords + "。";
        String user = "收信人：" + recipient + "。故事素材（保序）：" + String.join("；", stories)
                + "。语气：" + toneLabel(tone) + "。请按系统要求的 JSON 结构代笔。";
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.8,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        return mapper.writeValueAsString(body);
    }

    private String toneLabel(String tone) {
        return switch (tone) {
            case "TEAR" -> "戳泪点";
            case "FUN" -> "幽默";
            default -> "平淡";
        };
    }

    /** 调 OpenAI 兼容网关,返回模型输出的 content 字符串;包内可见——单测覆写喂伪造 JSON */
    String callGateway(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimUrl(baseUrl) + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        long start = System.currentTimeMillis();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("感恩信 AI 网关响应: status={}, 耗时={}ms",
                response.statusCode(), System.currentTimeMillis() - start);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("AI 网关返回 " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("AI 网关响应缺少 content");
        }
        return content.asText();
    }

    private String trimUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---- AI 输出解析与清洗(第二层防线) ----

    /** 解析模型 JSON:任一层结构缺失/类型错/故事数不符 → 抛异常由 generate 整封降级 */
    LetterGenerateResponse parseAiContent(String raw, String recipient, List<String> stories,
                                         String tone) throws Exception {
        JsonNode root = mapper.readTree(raw);
        List<String> opening = cleanVariants(readStringArray(root.get("opening"), "opening"));
        JsonNode storiesNode = root.get("stories");
        if (storiesNode == null || !storiesNode.isArray() || storiesNode.size() != stories.size()) {
            throw new IllegalStateException("AI stories 数量不符");
        }
        List<List<String>> storyVariants = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            JsonNode item = storiesNode.get(i);
            if (item == null || !isSameText(item.path("key").asText(), stories.get(i))) {
                throw new IllegalStateException("AI stories key 不匹配");
            }
            storyVariants.add(cleanVariants(readStringArray(item.get("variants"), "stories[" + i + "].variants")));
        }
        List<String> closing = cleanVariants(readStringArray(root.get("closing"), "closing"));

        // 段内存活 <2 → 降级写法补位(第三层防线兜底)
        opening = supplement(opening, LetterTextLib.openingVariants(recipient));
        for (int i = 0; i < storyVariants.size(); i++) {
            storyVariants.set(i, supplement(storyVariants.get(i), LetterTextLib.storyVariants(stories.get(i))));
        }
        closing = supplement(closing, LetterTextLib.closingVariants(tone));

        // 组装(与 FALLBACK 同结构;个别段被过滤补位仍标 AI——source 仅埋点用,用户无感)
        List<LetterSegment> segments = new ArrayList<>();
        int index = 0;
        segments.add(segment(index++, LetterSegmentRole.OPENING, null, opening));
        for (int i = 0; i < stories.size(); i++) {
            segments.add(segment(index++, LetterSegmentRole.STORY, stories.get(i), storyVariants.get(i)));
        }
        segments.add(segment(index, LetterSegmentRole.CLOSING, null, closing));
        LetterGenerateResponse response = new LetterGenerateResponse();
        response.setGenerationId(java.util.UUID.randomUUID().toString());
        response.setSource(LetterSource.AI);
        response.setParagraphs(segments);
        response.setSignText(LetterTextLib.SIGN_TEXT);
        return response;
    }

    private List<String> readStringArray(JsonNode node, String label) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("AI 输出缺少数组: " + label);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("AI 输出数组为空: " + label);
        }
        return result;
    }

    /** 逐 variant 清洗:违禁词命中/超长/空白即丢弃该条(保序去重) */
    private List<String> cleanVariants(List<String> variants) {
        Set<String> seen = new LinkedHashSet<>();
        for (String variant : variants) {
            String text = variant == null ? "" : variant.trim();
            if (text.isEmpty() || text.length() > MAX_AI_VARIANT_LENGTH) {
                continue;
            }
            if (DayTextLib.FORBIDDEN_WORDS.stream().anyMatch(text::contains)) {
                continue;
            }
            seen.add(text);
        }
        return new ArrayList<>(seen);
    }

    /** 存活条数 <2 → 用降级文案库补齐到 ≥2 */
    private List<String> supplement(List<String> survivors, List<String> fallback) {
        List<String> result = new ArrayList<>(survivors);
        for (String candidate : fallback) {
            if (result.size() >= 2) {
                break;
            }
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private LetterSegment segment(int index, LetterSegmentRole role, String storyKey, List<String> variants) {
        LetterSegment segment = new LetterSegment();
        segment.setIndex(index);
        segment.setRole(role);
        segment.setStoryKey(storyKey);
        segment.setVariants(variants);
        segment.setSelected(variants.get(0));
        return segment;
    }

    private boolean isSameText(String left, String right) {
        return left != null && left.trim().equals(right == null ? null : right.trim());
    }
}
