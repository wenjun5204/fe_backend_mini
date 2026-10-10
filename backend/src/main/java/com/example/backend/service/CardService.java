package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.CardDtos.CardKind;
import com.example.backend.dto.CardDtos.CardPayload;
import com.example.backend.dto.CardDtos.CardStatus;
import com.example.backend.dto.CardDtos.CreateCardRequest;
import com.example.backend.dto.CardDtos.DeleteCardResponse;
import com.example.backend.dto.CardDtos.LetterGenerateRequest;
import com.example.backend.dto.CardDtos.LetterGenerateResponse;
import com.example.backend.dto.CardDtos.MadeCard;
import com.example.backend.dto.CardDtos.MadeCardListResponse;
import com.example.backend.dto.CardDtos.ShareSceneResponse;
import com.example.backend.dto.CardDtos.TemplateCategory;
import com.example.backend.dto.CardDtos.TemplateInfo;
import com.example.backend.dto.CardDtos.TemplateListResponse;
import com.example.backend.dto.DayDtos.DayKind;
import com.example.backend.entity.DayRecordEntity;
import com.example.backend.entity.MadeCardEntity;
import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.repository.DayRecordRepository;
import com.example.backend.repository.MadeCardRepository;
import com.example.backend.repository.TimeCapsuleRepository;
import com.example.backend.repository.VisitorUnlockRepository;
import com.example.backend.dto.CapsuleDtos.CapsuleStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 念家时·模板馆卡片服务:模板清单/感恩信生成/三类卡片定稿存档/软删/分享场景码。
 * 越权/不存在/软删统一 40400 同文案(不泄露存在性,与 days 同口径);readOnly 零写库。
 * generateLetter 外层不加事务:配额走 AiLetterService.consumeQuota 的 REQUIRES_NEW 独立提交。
 */
@Service
public class CardService {

    /** 服务端固定落款常量(LETTER 定稿 signText 必须等于此值,防前端伪造) */
    private static final String SIGN_TEXT = LetterTextLib.SIGN_TEXT;
    private static final String VISITOR_PAGE_PREFIX = "pages/visitor/visitor?scene=";
    private static final Pattern STYLE_KEYS = Pattern.compile("SUNSET|NIGHT|MINT");
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SCENE_LENGTH = 8;
    private static final int MAX_PARAGRAPHS = 9;
    private static final int MAX_PARAGRAPH_CHARS = 120;

    private final MadeCardRepository cardRepository;
    private final TimeCapsuleRepository capsuleRepository;
    private final DayRecordRepository dayRecordRepository;
    private final VisitorUnlockRepository visitorUnlockRepository;
    private final AiLetterService aiLetterService;
    private final ObjectMapper mapper;

    public CardService(MadeCardRepository cardRepository,
                       TimeCapsuleRepository capsuleRepository,
                       DayRecordRepository dayRecordRepository,
                       VisitorUnlockRepository visitorUnlockRepository,
                       AiLetterService aiLetterService,
                       ObjectMapper mapper) {
        this.cardRepository = cardRepository;
        this.capsuleRepository = capsuleRepository;
        this.dayRecordRepository = dayRecordRepository;
        this.visitorUnlockRepository = visitorUnlockRepository;
        this.aiLetterService = aiLetterService;
        this.mapper = mapper;
    }

    // ---- 模板馆清单 ----

    @Transactional(readOnly = true)
    public TemplateListResponse listTemplates(TemplateCategory category) {
        CurrentUser.get();
        List<TemplateInfo> items = category == null
                ? TemplateCatalog.list()
                : TemplateCatalog.listByCategory(category);
        TemplateListResponse response = new TemplateListResponse();
        response.setItems(items);
        return response;
    }

    // ---- 感恩信生成 ----

    /** 校验(40000 优先,不耗额度)→ REQUIRES_NEW 配额自增(>5 抛 40300)→ AI/降级生成 */
    public LetterGenerateResponse generateLetter(LetterGenerateRequest request) {
        Long userId = CurrentUser.get();
        String recipient = validateRecipient(request == null ? null : request.getRecipient());
        List<String> stories = validateStories(request == null ? null : request.getStories());
        String tone = validateTone(request == null ? null : request.getTone());
        aiLetterService.consumeQuota(userId);
        return aiLetterService.generate(recipient, stories, tone);
    }

    private String validateRecipient(String recipient) {
        if (recipient == null || recipient.isBlank() || recipient.trim().length() > 12) {
            throw ApiException.param("请您把收信人称呼填写完整");
        }
        String trimmed = recipient.trim();
        if (DayTextLib.FORBIDDEN_WORDS.stream().anyMatch(trimmed::contains)) {
            throw ApiException.param("这个称呼里有不合适的词，麻烦换一个");
        }
        return trimmed;
    }

    private List<String> validateStories(List<String> stories) {
        if (stories == null || stories.isEmpty()) {
            throw ApiException.param("请您至少选一个故事");
        }
        if (stories.size() > 5) {
            throw ApiException.param("故事最多选 5 个");
        }
        for (String story : stories) {
            if (story == null || !LetterTextLib.StoryKeys.ALL.contains(story)) {
                throw ApiException.param("请您从给定的故事里选");
            }
        }
        if (stories.stream().distinct().count() != stories.size()) {
            throw ApiException.param("同一个故事选一次就够啦");
        }
        return stories;
    }

    private String validateTone(String tone) {
        if (tone == null || !List.of("TEAR", "FUN", "CALM").contains(tone)) {
            throw ApiException.param("请您选一个信件的语气");
        }
        return tone;
    }

    // ---- 卡片定稿存档 ----

    @Transactional
    public MadeCard create(CreateCardRequest request) {
        Long userId = CurrentUser.get();
        if (request == null || request.getTemplateKind() == null || request.getPayload() == null) {
            throw ApiException.param("请您把卡片内容填写完整");
        }
        CardKind kind = parseKind(request.getTemplateKind());
        CardPayload payload = request.getPayload();
        MadeCardEntity card = new MadeCardEntity();
        card.setUserId(userId);
        card.setTemplateKind(kind);
        card.setStatus(CardStatus.ACTIVE);
        card.setCreatedAt(LocalDateTime.now());
        switch (kind) {
            case LETTER -> applyLetter(card, payload, request);
            case DAYS_CARD -> applyDaysCard(card, payload, request, userId);
            case CAPSULE -> applyCapsule(card, payload, request, userId);
        }
        cardRepository.save(card);
        return toDto(card, 0);
    }

    private CardKind parseKind(String templateKind) {
        try {
            return CardKind.valueOf(templateKind);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.param("请您选择要做的卡片类型");
        }
    }

    private void applyLetter(MadeCardEntity card, CardPayload payload, CreateCardRequest request) {
        if (payload.getRecipient() == null || payload.getRecipient().isBlank()
                || payload.getRecipient().trim().length() > 12) {
            throw ApiException.param("请您把收信人称呼填写完整");
        }
        if (payload.getTone() == null || !List.of("TEAR", "FUN", "CALM").contains(payload.getTone())) {
            throw ApiException.param("请您选一个信件的语气");
        }
        if (payload.getParagraphs() == null || payload.getParagraphs().isEmpty()
                || payload.getParagraphs().size() > MAX_PARAGRAPHS) {
            throw ApiException.param("信件段落要在 1 到 9 段之间");
        }
        for (String paragraph : payload.getParagraphs()) {
            if (paragraph == null || paragraph.isBlank() || paragraph.trim().length() > MAX_PARAGRAPH_CHARS) {
                throw ApiException.param("信件每段不超过 " + MAX_PARAGRAPH_CHARS + " 字");
            }
            if (DayTextLib.FORBIDDEN_WORDS.stream().anyMatch(paragraph::contains)) {
                throw ApiException.param("这段话里有不合适的词，麻烦改一改");
            }
        }
        if (!SIGN_TEXT.equals(payload.getSignText())) {
            throw ApiException.param("落款保持默认就好");
        }
        // 不适用字段出现即 40000;styleKey 忽略请求值强制 NIGHT
        rejectForeignFields(payload, request, "LETTER",
                List.of("relation", "startDate", "coverHint", "openDate", "type"));
        payload.setRecipient(payload.getRecipient().trim());
        for (int i = 0; i < payload.getParagraphs().size(); i++) {
            payload.getParagraphs().set(i, payload.getParagraphs().get(i).trim());
        }
        payload.setStyleKey("NIGHT");
        card.setPayload(serialize(payload));
    }

    private void applyDaysCard(MadeCardEntity card, CardPayload payload, CreateCardRequest request,
                               Long userId) {
        if (payload.getRelation() == null || payload.getRelation().isBlank()
                || payload.getRelation().trim().length() > 12) {
            throw ApiException.param("请您把称呼填写完整");
        }
        if (payload.getStyleKey() == null || !STYLE_KEYS.matcher(payload.getStyleKey()).matches()) {
            throw ApiException.param("请您选一个卡片样式");
        }
        rejectForeignFields(payload, request, "DAYS_CARD",
                List.of("recipient", "tone", "paragraphs", "signText", "coverHint", "openDate", "type"));
        LocalDate startDate = parseDate(payload.getStartDate());
        if (startDate.isAfter(LocalDate.now())) {
            throw ApiException.param("起始日不能是未来");
        }
        if (request.getSourceDayId() != null) {
            // 关联本人 TOGETHER DayRecord:服务端以该记录起始日覆盖 startDate(单一权威)
            DayRecordEntity record = dayRecordRepository.findById(request.getSourceDayId()).orElse(null);
            if (record == null) {
                throw ApiException.notFound("没有找到这条日子");
            }
            if (!record.getUserId().equals(userId)) {
                throw ApiException.param("只能关联自己的日子");
            }
            if (record.getKind() != DayKind.TOGETHER) {
                throw ApiException.param("只能关联「在一起」的日子");
            }
            card.setSourceDayId(record.getId());
            startDate = LocalDate.of(record.getCreatedAt().getYear(), record.getMonth(), record.getDay());
        }
        payload.setRelation(payload.getRelation().trim());
        payload.setStartDate(startDate.toString());
        card.setPayload(serialize(payload));
    }

    private void applyCapsule(MadeCardEntity card, CardPayload payload, CreateCardRequest request,
                              Long userId) {
        Long capsuleId = request.getCapsuleId() != null ? request.getCapsuleId() : payload.getCapsuleId();
        if (capsuleId == null) {
            throw ApiException.param("请您先封存一个时光胶囊");
        }
        rejectForeignFields(payload, request, "CAPSULE",
                List.of("recipient", "tone", "paragraphs", "signText", "relation", "startDate"));
        TimeCapsuleEntity capsule = capsuleRepository.findById(capsuleId).orElse(null);
        if (capsule == null || !capsule.getUserId().equals(userId)
                || capsule.getStatus() != CapsuleStatus.SEALED) {
            throw ApiException.notFound("没有找到这枚胶囊");
        }
        // 胶囊实体为权威:coverHint/openDate/type 以胶囊覆盖,防邀请函与胶囊状态漂移
        payload.setCapsuleId(capsule.getId());
        payload.setCoverHint(capsule.getCoverHint());
        payload.setOpenDate(capsule.getOpenDate().toString());
        payload.setType(capsule.getType().name());
        payload.setStyleKey("MINT");
        card.setCapsuleId(capsule.getId());
        card.setPayload(serialize(payload));
    }

    /** 不适用字段出现(如 LETTER 带 capsuleId)→ 40000;top-level 关联字段按 kind 校验 */
    private void rejectForeignFields(CardPayload payload, CreateCardRequest request, String kind,
                                     List<String> payloadFields) {
        for (String field : payloadFields) {
            if (readField(payload, field) != null) {
                throw ApiException.param("这张卡片的填写内容与类型不匹配，请您检查");
            }
        }
        if (payload.getCapsuleId() != null && kind != null && !kind.equals("CAPSULE")) {
            throw ApiException.param("这张卡片的填写内容与类型不匹配，请您检查");
        }
        if (request.getCapsuleId() != null && !kind.equals("CAPSULE")) {
            throw ApiException.param("这张卡片的填写内容与类型不匹配，请您检查");
        }
        if (request.getSourceDayId() != null && !kind.equals("DAYS_CARD")) {
            throw ApiException.param("这张卡片的填写内容与类型不匹配，请您检查");
        }
    }

    private Object readField(CardPayload payload, String field) {
        return switch (field) {
            case "relation" -> payload.getRelation();
            case "startDate" -> payload.getStartDate();
            case "coverHint" -> payload.getCoverHint();
            case "openDate" -> payload.getOpenDate();
            case "type" -> payload.getType();
            case "recipient" -> payload.getRecipient();
            case "tone" -> payload.getTone();
            case "paragraphs" -> payload.getParagraphs();
            case "signText" -> payload.getSignText();
            default -> null;
        };
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw ApiException.param("请您把日期选好");
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw ApiException.param("请您把日期选好");
        }
    }

    private String serialize(CardPayload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("卡片内容序列化失败", e);
        }
    }

    // ---- 我的卡片 ----

    @Transactional(readOnly = true)
    public MadeCardListResponse listMyCards() {
        Long userId = CurrentUser.get();
        List<MadeCardEntity> cards =
                cardRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, CardStatus.ACTIVE);
        List<Long> cardIds = cards.stream().map(MadeCardEntity::getId).toList();
        Map<Long, Integer> heartsByCard = new HashMap<>();
        if (!cardIds.isEmpty()) {
            for (Object[] row : visitorUnlockRepository.sumHeartsByCardIds(cardIds)) {
                heartsByCard.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        int heartsReceived = heartsByCard.values().stream().mapToInt(Integer::intValue).sum();
        MadeCardListResponse response = new MadeCardListResponse();
        response.setItems(cards.stream().map(card -> toDto(card,
                heartsByCard.getOrDefault(card.getId(), 0))).toList());
        response.setTotal(cards.size());
        response.setHeartsReceived(heartsReceived);
        return response;
    }

    @Transactional(readOnly = true)
    public MadeCard getMyCard(Long id) {
        CurrentUser.get();
        MadeCardEntity card = getOwnActiveCard(id);
        return toDto(card, (int) visitorUnlockRepository.sumHeartsByCard(card.getId()));
    }

    /** 软删幂等:重复删返回 true;他人/不存在 40400;visitor_unlock 行保留(历史统计不追溯清零) */
    @Transactional
    public DeleteCardResponse delete(Long id) {
        Long userId = CurrentUser.get();
        MadeCardEntity card = cardRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这张卡片"));
        if (!card.getUserId().equals(userId)) {
            throw ApiException.notFound("没有找到这张卡片");
        }
        card.setStatus(CardStatus.DELETED);
        cardRepository.save(card);
        DeleteCardResponse response = new DeleteCardResponse();
        response.setDeleted(true);
        return response;
    }

    /** scene 签发幂等:同卡片恒同码;8 位 base62 SecureRandom,唯一索引兜底撞码重生成(≤5 次) */
    @Transactional
    public ShareSceneResponse share(Long id) {
        Long userId = CurrentUser.get();
        MadeCardEntity card = getOwnActiveCard(id);
        if (card.getShareScene() == null) {
            for (int attempt = 0; attempt < 5; attempt++) {
                String candidate = randomScene();
                if (cardRepository.existsByShareScene(candidate)) {
                    continue;
                }
                try {
                    card.setShareScene(candidate);
                    cardRepository.saveAndFlush(card);
                    break;
                } catch (DataIntegrityViolationException e) {
                    card.setShareScene(null);
                }
            }
            if (card.getShareScene() == null) {
                throw new IllegalStateException("场景码签发失败,请稍后再试");
            }
        }
        ShareSceneResponse response = new ShareSceneResponse();
        response.setScene(card.getShareScene());
        response.setPagePath(VISITOR_PAGE_PREFIX + card.getShareScene());
        return response;
    }

    private String randomScene() {
        StringBuilder builder = new StringBuilder(SCENE_LENGTH);
        for (int i = 0; i < SCENE_LENGTH; i++) {
            builder.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return builder.toString();
    }

    private MadeCardEntity getOwnActiveCard(Long id) {
        MadeCardEntity card = cardRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这张卡片"));
        if (!card.getUserId().equals(CurrentUser.get()) || card.getStatus() != CardStatus.ACTIVE) {
            throw ApiException.notFound("没有找到这张卡片");
        }
        return card;
    }

    private MadeCard toDto(MadeCardEntity card, int totalHearts) {
        MadeCard dto = new MadeCard();
        dto.setId(card.getId());
        dto.setTemplateKind(card.getTemplateKind());
        dto.setPayload(parsePayload(card));
        dto.setSourceDayId(card.getSourceDayId());
        dto.setCapsuleId(card.getCapsuleId());
        dto.setShareScene(card.getShareScene());
        dto.setStatus(card.getStatus());
        dto.setCreatedAt(card.getCreatedAt().toString());
        dto.setTotalHearts(totalHearts);
        return dto;
    }

    private CardPayload parsePayload(MadeCardEntity card) {
        try {
            return mapper.readValue(card.getPayload(), CardPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("卡片内容解析失败: id=" + card.getId(), e);
        }
    }

    /** 相伴天数口径与 days 同源:max(1, between(start, today)) */
    static int daysTogether(String startDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.now());
        return (int) Math.max(1, days);
    }
}
