package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.CapsuleDtos.CapsuleCreateRequest;
import com.example.backend.dto.CapsuleDtos.CapsuleListResponse;
import com.example.backend.dto.CapsuleDtos.CapsuleOpenResponse;
import com.example.backend.dto.CapsuleDtos.CapsuleOpenRule;
import com.example.backend.dto.CapsuleDtos.CapsuleStatus;
import com.example.backend.dto.CapsuleDtos.CapsuleType;
import com.example.backend.dto.CapsuleDtos.TimeCapsule;
import com.example.backend.entity.TimeCapsuleEntity;
import com.example.backend.repository.TimeCapsuleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 念家时·时光胶囊:封存(明文存+读门控)/列表(待开红点数据源)/详情/开箱(幂等)。
 * 越权/不存在统一 40400;SEALED 态任何本人端点不下发 contentText/photoUrl(NON_NULL 省略键);
 * open 判定唯一看 openDate(ONE_YEAR_LATER=封存日+365 天由服务端写入)。
 */
@Service
public class CapsuleService {

    private final TimeCapsuleRepository capsuleRepository;

    public CapsuleService(TimeCapsuleRepository capsuleRepository) {
        this.capsuleRepository = capsuleRepository;
    }

    @Transactional
    public TimeCapsule seal(CapsuleCreateRequest request) {
        Long userId = CurrentUser.get();
        if (request == null) {
            throw ApiException.param("请您把胶囊内容填写完整");
        }
        CapsuleType type = parseEnum(request.getType(), CapsuleType.class, "请您选择胶囊类型");
        CapsuleOpenRule openRule = parseEnum(request.getOpenRule(), CapsuleOpenRule.class,
                "请您选择开箱的日子");
        String coverHint = requireLength(request.getCoverHint(), 1, 40, "封面提示语要在 1 到 40 字之间");
        String contentText = requireLength(request.getContentText(), 1, 500, "信件内容要在 1 到 500 字之间");
        assertCleanText(coverHint);
        assertCleanText(contentText);
        LocalDate openDate;
        if (openRule == CapsuleOpenRule.ONE_YEAR_LATER) {
            // 服务端权威:封存日+365 天,忽略请求 openDate
            openDate = LocalDate.now().plusDays(365);
        } else {
            openDate = parseFutureDate(request.getOpenDate());
        }
        TimeCapsuleEntity capsule = new TimeCapsuleEntity();
        capsule.setUserId(userId);
        capsule.setType(type);
        capsule.setOpenRule(openRule);
        capsule.setOpenDate(openDate);
        capsule.setCoverHint(coverHint);
        capsule.setContentText(contentText);
        capsule.setPhotoUrl(normalizePhotoUrl(request.getPhotoUrl()));
        capsule.setStatus(CapsuleStatus.SEALED);
        capsule.setSealedAt(LocalDateTime.now());
        capsuleRepository.save(capsule);
        return toDto(capsule, false);
    }

    /** SEALED 按 openDate 升序在前、OPENED 按 openedAt 降序排后;dueCount=待开数 */
    @Transactional(readOnly = true)
    public CapsuleListResponse listMine() {
        Long userId = CurrentUser.get();
        List<TimeCapsule> items = new ArrayList<>();
        int dueCount = 0;
        for (TimeCapsuleEntity sealed : capsuleRepository
                .findByUserIdAndStatusOrderByOpenDateAsc(userId, CapsuleStatus.SEALED)) {
            boolean dueNow = isDue(sealed);
            items.add(toDto(sealed, dueNow));
            if (dueNow) {
                dueCount++;
            }
        }
        for (TimeCapsuleEntity opened : capsuleRepository
                .findByUserIdAndStatusOrderByOpenedAtDesc(userId, CapsuleStatus.OPENED)) {
            items.add(toDto(opened, false));
        }
        CapsuleListResponse response = new CapsuleListResponse();
        response.setItems(items);
        response.setDueCount(dueCount);
        return response;
    }

    /** SEALED 只回封面字段+倒计时(内容字段不下发,非置空);OPENED 回完整内容 */
    @Transactional(readOnly = true)
    public TimeCapsule getMine(Long id) {
        CurrentUser.get();
        TimeCapsuleEntity capsule = getOwnCapsule(id);
        return toDto(capsule, isDue(capsule));
    }

    /** 未到期 40900;SEALED→OPENED 落 openedAt 并首次下发内容;已 OPENED 幂等返回首次结果 */
    @Transactional
    public CapsuleOpenResponse open(Long id) {
        CurrentUser.get();
        TimeCapsuleEntity capsule = getOwnCapsule(id);
        if (capsule.getStatus() == CapsuleStatus.SEALED) {
            if (LocalDate.now().isBefore(capsule.getOpenDate())) {
                throw ApiException.conflict("还没到开箱的日子");
            }
            capsule.setStatus(CapsuleStatus.OPENED);
            capsule.setOpenedAt(LocalDateTime.now());
            capsuleRepository.save(capsule);
        }
        CapsuleOpenResponse response = new CapsuleOpenResponse();
        response.setId(capsule.getId());
        response.setType(capsule.getType());
        response.setOpenRule(capsule.getOpenRule());
        response.setOpenDate(capsule.getOpenDate().toString());
        response.setCoverHint(capsule.getCoverHint());
        response.setContentText(capsule.getContentText());
        response.setPhotoUrl(capsule.getPhotoUrl());
        response.setStatus(CapsuleStatus.OPENED.name());
        response.setSealedAt(capsule.getSealedAt().toString());
        response.setOpenedAt(capsule.getOpenedAt() == null ? null : capsule.getOpenedAt().toString());
        response.setDaysUntilOpen(0);
        return response;
    }

    // ---- 内部 ----

    private TimeCapsuleEntity getOwnCapsule(Long id) {
        TimeCapsuleEntity capsule = capsuleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("没有找到这枚胶囊"));
        if (!capsule.getUserId().equals(CurrentUser.get())) {
            throw ApiException.notFound("没有找到这枚胶囊");
        }
        return capsule;
    }

    /** SEALED 态不下发内容字段(toDto 不赋值,NON_NULL 序列化省略键) */
    private TimeCapsule toDto(TimeCapsuleEntity capsule, boolean dueNow) {
        TimeCapsule dto = new TimeCapsule();
        dto.setId(capsule.getId());
        dto.setType(capsule.getType());
        dto.setOpenRule(capsule.getOpenRule());
        dto.setOpenDate(capsule.getOpenDate().toString());
        dto.setCoverHint(capsule.getCoverHint());
        if (capsule.getStatus() == CapsuleStatus.OPENED) {
            dto.setContentText(capsule.getContentText());
            dto.setPhotoUrl(capsule.getPhotoUrl());
        }
        dto.setStatus(capsule.getStatus());
        dto.setSealedAt(capsule.getSealedAt().toString());
        dto.setOpenedAt(capsule.getOpenedAt() == null ? null : capsule.getOpenedAt().toString());
        dto.setDaysUntilOpen(daysUntilOpen(capsule));
        dto.setDueNow(dueNow);
        return dto;
    }

    static boolean isDue(TimeCapsuleEntity capsule) {
        return capsule.getStatus() == CapsuleStatus.SEALED && !LocalDate.now().isBefore(capsule.getOpenDate());
    }

    static int daysUntilOpen(TimeCapsuleEntity capsule) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), capsule.getOpenDate()));
    }

    private LocalDate parseFutureDate(String date) {
        if (date == null || date.isBlank()) {
            throw ApiException.param("开箱日要选未来的日子");
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw ApiException.param("开箱日要选未来的日子");
        }
        if (!parsed.isAfter(LocalDate.now())) {
            throw ApiException.param("开箱日要选未来的日子");
        }
        return parsed;
    }

    private String requireLength(String text, int min, int max, String message) {
        if (text == null || text.trim().length() < min || text.trim().length() > max) {
            throw ApiException.param(message);
        }
        return text.trim();
    }

    private void assertCleanText(String text) {
        if (DayTextLib.FORBIDDEN_WORDS.stream().anyMatch(text::contains)) {
            throw ApiException.param("这段话里有不合适的词，麻烦改一改");
        }
    }

    private String normalizePhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (photoUrl.trim().length() > 255) {
            throw ApiException.param("照片链接太长了");
        }
        return photoUrl.trim();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String message) {
        if (value == null) {
            throw ApiException.param(message);
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ApiException.param(message);
        }
    }
}
