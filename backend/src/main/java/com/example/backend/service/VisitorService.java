package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.dto.CardDtos.CardKind;
import com.example.backend.dto.CardDtos.CardPayload;
import com.example.backend.dto.CardDtos.CardStatus;
import com.example.backend.dto.VisitorDtos.CtaCopy;
import com.example.backend.dto.VisitorDtos.HeartsRequest;
import com.example.backend.dto.VisitorDtos.HiddenContent;
import com.example.backend.dto.VisitorDtos.UnlockedContentResponse;
import com.example.backend.dto.VisitorDtos.VisitorCardView;
import com.example.backend.dto.VisitorDtos.VisitorPublicPayload;
import com.example.backend.dto.VisitorDtos.VisitorUnlockState;
import com.example.backend.entity.FamilyMomentEntity;
import com.example.backend.entity.FamilyMomentEntity.MomentStatus;
import com.example.backend.entity.MadeCardEntity;
import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.entity.VisitorUnlockEntity;
import com.example.backend.repository.FamilyMomentRepository;
import com.example.backend.repository.MadeCardRepository;
import com.example.backend.repository.TimeCapsuleRepository;
import com.example.backend.repository.VisitorUnlockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 念家时·免鉴权访客组(经 /api/visitor/**,AuthInterceptor 排除,永不 40100):
 * 落地体面内容(脱敏白名单,无 PII)/点亮心(device 维度封顶 3)/解锁取隐藏内容。
 * 越权/软删/scene 无效统一 40400(响应逐字节一致,不泄露存在性);
 * hearts 软频控 42900:内存滑动窗口(单设备单卡片 >3 次/分钟),多实例误差可容忍(设计裁定)。
 */
@Service
public class VisitorService {

    private static final Pattern SCENE_PATTERN = Pattern.compile("[0-9a-zA-Z]{8,12}");
    /** deviceId 白名单:hex/UUID 字符集(32-64 字符) */
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[0-9a-fA-F-]{32,64}");
    private static final int HEARTS_TO_UNLOCK = 3;
    private static final long RATE_WINDOW_MILLIS = 60_000;
    private static final int RATE_WINDOW_LIMIT = 3;
    /** 频控窗口键容量上限:防内存膨胀(超限整体清空,软频控精度损失可接受) */
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final MadeCardRepository cardRepository;
    private final TimeCapsuleRepository capsuleRepository;
    private final VisitorUnlockRepository visitorUnlockRepository;
    private final FamilyMomentRepository momentRepository;
    private final ObjectMapper mapper;

    /** 软频控滑动窗口:key=deviceId:cardId → 时间戳队列(仅内存,重启即清) */
    private final ConcurrentHashMap<String, Deque<Long>> heartWindows = new ConcurrentHashMap<>();

    public VisitorService(MadeCardRepository cardRepository,
                          TimeCapsuleRepository capsuleRepository,
                          VisitorUnlockRepository visitorUnlockRepository,
                          FamilyMomentRepository momentRepository,
                          ObjectMapper mapper) {
        this.cardRepository = cardRepository;
        this.capsuleRepository = capsuleRepository;
        this.visitorUnlockRepository = visitorUnlockRepository;
        this.momentRepository = momentRepository;
        this.mapper = mapper;
    }

    // ---- 落地页 ----

    @Transactional(readOnly = true)
    public VisitorCardView getCard(String scene, String deviceId) {
        String device = validateDeviceId(deviceId);
        MadeCardEntity card = findActiveCard(scene);
        VisitorCardView view = new VisitorCardView();
        view.setTemplateKind(card.getTemplateKind());
        view.setPublicPayload(publicPayload(card));
        view.setLockedTeaser(CardTextLib.lockedTeaser(card.getTemplateKind()));
        VisitorUnlockEntity unlock = findUnlock(card.getId(), device);
        int hearts = unlock == null ? 0 : unlock.getHearts();
        view.setHearts(hearts);
        view.setUnlocked(hearts >= HEARTS_TO_UNLOCK);
        return view;
    }

    private VisitorPublicPayload publicPayload(MadeCardEntity card) {
        CardPayload payload = parsePayload(card);
        VisitorPublicPayload view = new VisitorPublicPayload();
        switch (card.getTemplateKind()) {
            case LETTER -> {
                view.setRecipient(payload.getRecipient());
                // 体面内容=前 3 段+落款(第 4 段起留作解锁钩子)
                List<String> paragraphs = payload.getParagraphs();
                view.setParagraphs(paragraphs.stream().limit(3).toList());
                view.setSignText(payload.getSignText());
                view.setStyleKey(payload.getStyleKey());
            }
            case DAYS_CARD -> {
                view.setRelation(payload.getRelation());
                view.setStartDate(payload.getStartDate());
                view.setDaysTogether(CardService.daysTogether(payload.getStartDate()));
                view.setStyleKey(payload.getStyleKey());
            }
            case CAPSULE -> {
                TimeCapsuleEntity capsule = capsuleRepository.findById(card.getCapsuleId())
                        .orElseThrow(() -> ApiException.notFound("没有找到这张卡片"));
                view.setType(capsule.getType());
                view.setCoverHint(capsule.getCoverHint());
                view.setOpenDate(capsule.getOpenDate().toString());
                long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), capsule.getOpenDate());
                view.setDaysUntilOpen((int) Math.max(0, days));
                view.setStyleKey(payload.getStyleKey());
            }
            case BIRTHDAY_CARD -> {
                view.setRelation(payload.getRelation());
                // 契约:逐 momentId 取 ACTIVE 瞬间的 capability 路径,已删素材跳过(≤3)
                List<String> photoUrls = new java.util.ArrayList<>();
                if (payload.getMomentIds() != null) {
                    for (Long momentId : payload.getMomentIds()) {
                        FamilyMomentEntity moment = momentRepository.findById(momentId).orElse(null);
                        if (moment != null && moment.getStatus() == MomentStatus.ACTIVE) {
                            photoUrls.add("/api/photos/" + moment.getPhotoId());
                        }
                    }
                }
                view.setPhotoUrls(photoUrls);
                view.setKicker(CardTextLib.BIRTHDAY_KICKER);
                view.setStyleKey(payload.getStyleKey());
            }
        }
        return view;
    }

    // ---- 点亮心 ----

    /**
     * 频控先查(全部调用计入窗口,>3 次/分钟 42900;合法用户连点 3 心不受影响),
     * upsert 幂等:hearts=min(3, hearts+1),已满 3 再调恒返回当前态不再计数。
     */
    @Transactional
    public VisitorUnlockState lightHeart(String scene, HeartsRequest request) {
        String device = validateDeviceId(request == null ? null : request.getDeviceId());
        MadeCardEntity card = findActiveCard(scene);
        checkHeartRate(device + ":" + card.getId());
        VisitorUnlockEntity unlock = visitorUnlockRepository
                .findByCardIdAndDeviceId(card.getId(), device)
                .orElseGet(() -> {
                    VisitorUnlockEntity created = new VisitorUnlockEntity();
                    created.setCardId(card.getId());
                    created.setDeviceId(device);
                    created.setHearts(0);
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });
        unlock.setHearts(Math.min(HEARTS_TO_UNLOCK, unlock.getHearts() + 1));
        if (unlock.getHearts() >= HEARTS_TO_UNLOCK && unlock.getUnlockedAt() == null) {
            unlock.setUnlockedAt(LocalDateTime.now());
        }
        unlock.setUpdatedAt(LocalDateTime.now());
        visitorUnlockRepository.save(unlock);
        VisitorUnlockState state = new VisitorUnlockState();
        state.setHearts(unlock.getHearts());
        state.setUnlocked(unlock.getHearts() >= HEARTS_TO_UNLOCK);
        state.setRemainingHint(CardTextLib.remainingHint(unlock.getHearts()));
        state.setTotalHearts((int) visitorUnlockRepository.sumHeartsByCard(card.getId()));
        return state;
    }

    /** 42900 软频控:内存滑动窗口;容量超限整体清空(防内存膨胀,精度损失可接受) */
    private void checkHeartRate(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> window = heartWindows.computeIfAbsent(key, k -> {
            if (heartWindows.size() >= MAX_TRACKED_KEYS) {
                heartWindows.clear();
            }
            return new ArrayDeque<>();
        });
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= RATE_WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= RATE_WINDOW_LIMIT) {
                throw new ApiException("42900", "点太快啦，歇一下再点");
            }
            window.addLast(now);
        }
    }

    /** 测试钩子:模拟频控窗口过期(生产无调用方);幂等用例在窗口清空后断言恒 3/true */
    public void resetHeartRateWindowForTests() {
        heartWindows.clear();
    }

    // ---- 解锁取隐藏内容 ----

    /** 未解锁 40900;CAPSULE 未到期返回预置悬念文案(真实内容零泄露) */
    @Transactional(readOnly = true)
    public UnlockedContentResponse getContent(String scene, String deviceId) {
        String device = validateDeviceId(deviceId);
        MadeCardEntity card = findActiveCard(scene);
        VisitorUnlockEntity unlock = findUnlock(card.getId(), device);
        int hearts = unlock == null ? 0 : unlock.getHearts();
        if (hearts < HEARTS_TO_UNLOCK) {
            throw ApiException.conflict("再点亮 " + HEARTS_TO_UNLOCK + " 颗心就能看啦");
        }
        CardPayload payload = parsePayload(card);
        HiddenContent hidden = new HiddenContent();
        switch (card.getTemplateKind()) {
            case LETTER -> {
                hidden.setParagraphs(payload.getParagraphs());
                hidden.setSignText(payload.getSignText());
            }
            case DAYS_CARD -> hidden.setHiddenLine(
                    CardTextLib.hiddenLine(CardService.daysTogether(payload.getStartDate())));
            case BIRTHDAY_CARD -> hidden.setBlessingText(payload.getBlessingText());
            case CAPSULE -> {
                TimeCapsuleEntity capsule = capsuleRepository.findById(card.getCapsuleId())
                        .orElseThrow(() -> ApiException.notFound("没有找到这张卡片"));
                if (LocalDate.now().isBefore(capsule.getOpenDate())) {
                    // 内容门控:未到期只回预置悬念文案,真实 contentText 永不进访客响应
                    hidden.setContentText(CardTextLib.CAPSULE_SUSPENSE);
                    hidden.setPhotoUrl(null);
                } else {
                    hidden.setContentText(capsule.getContentText());
                    // photoId 优先公式与本人端同源(存在时恒 /api/photos/{photoId})
                    hidden.setPhotoUrl(CapsuleService.photoUrl(capsule));
                }
            }
        }
        CtaCopy cta = new CtaCopy();
        cta.setTitle(CardTextLib.CTA_TITLE);
        cta.setSub(CardTextLib.CTA_SUB);
        cta.setButtonText(CardTextLib.CTA_BUTTON);
        UnlockedContentResponse response = new UnlockedContentResponse();
        response.setTemplateKind(card.getTemplateKind());
        response.setHiddenContent(hidden);
        response.setCtaCopy(cta);
        return response;
    }

    // ---- 内部 ----

    private String validateDeviceId(String deviceId) {
        if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            throw ApiException.param("设备标识不合法，请刷新页面重试");
        }
        return deviceId;
    }

    /** scene 无效/软删统一 40400 同文案(响应逐字节一致,不泄露存在性) */
    private MadeCardEntity findActiveCard(String scene) {
        if (scene == null || !SCENE_PATTERN.matcher(scene).matches()) {
            throw ApiException.notFound("没有找到这张卡片");
        }
        return cardRepository.findByShareSceneAndStatus(scene, CardStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("没有找到这张卡片"));
    }

    private VisitorUnlockEntity findUnlock(Long cardId, String deviceId) {
        return visitorUnlockRepository.findByCardIdAndDeviceId(cardId, deviceId).orElse(null);
    }

    private CardPayload parsePayload(MadeCardEntity card) {
        try {
            return mapper.readValue(card.getPayload(), CardPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("卡片内容解析失败: id=" + card.getId(), e);
        }
    }
}
