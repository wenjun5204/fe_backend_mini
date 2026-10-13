package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.CardDtos.TemplateInfo;
import com.example.backend.dto.CardDtos.TemplateKey;
import com.example.backend.dto.FestivalDtos.CancelReservationResponse;
import com.example.backend.dto.FestivalDtos.FestivalDueItem;
import com.example.backend.dto.FestivalDtos.FestivalDueResponse;
import com.example.backend.dto.FestivalDtos.FestivalReservation;
import com.example.backend.dto.FestivalDtos.FestivalReservationRequest;
import com.example.backend.dto.FestivalDtos.ReservationListItem;
import com.example.backend.dto.FestivalDtos.ReservationListResponse;
import com.example.backend.entity.FestivalReservationEntity;
import com.example.backend.repository.FestivalReservationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 节日/上线预约:登记幂等(uk(user_id,template_key) 物理保证),仅数据不下发——
 * 真实 subscribeMessage.send 属部署联调阶段,本服务零推送调用(验收红线)。
 * festivalDate 是 TemplateCatalog 静态配置,到期后由运营在下个版本滚动更新(MVP 接受)。
 */
@Service
public class FestivalService {

    /** festivalLabel 的非中文段即节日主表情,如「父亲节 👔」→「👔」;无则 null */
    private static final Pattern NON_CHINESE = Pattern.compile("[^\\u4e00-\\u9fa5]+");

    private final FestivalReservationRepository reservationRepository;

    public FestivalService(FestivalReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /** 预约登记(幂等:重复预约返回既有记录,首次登记时间不变);templateKey 越值域 40400 不泄露存在性 */
    @Transactional
    public FestivalReservation reserve(FestivalReservationRequest request) {
        Long userId = CurrentUser.get();
        String raw = request == null || request.getTemplateKey() == null
                ? null : request.getTemplateKey().trim();
        if (raw == null || raw.isBlank()) {
            throw ApiException.param("请您选择一个模板");
        }
        TemplateKey templateKey = parseKeyOrNotFound(raw);
        FestivalReservationEntity entity = findMine(userId, templateKey);
        if (entity == null) {
            FestivalReservationEntity created = new FestivalReservationEntity();
            created.setUserId(userId);
            created.setTemplateKey(templateKey.name());
            created.setCreatedAt(LocalDateTime.now());
            try {
                reservationRepository.saveAndFlush(created);
                entity = created;
            } catch (DataIntegrityViolationException e) {
                // 并发撞唯一约束:回读首次登记行(幂等);撞码概率 ~0,预检为主
                entity = findMine(userId, templateKey);
            }
        }
        if (entity == null) {
            throw new IllegalStateException("预约登记失败，请稍后再试");
        }
        return toReservation(templateKey, entity);
    }

    /** 我的预约清单(按首次登记时间降序),逐行 join TemplateCatalog 组装展示字段 */
    @Transactional(readOnly = true)
    public ReservationListResponse listMine() {
        Long userId = CurrentUser.get();
        List<ReservationListItem> items = new ArrayList<>();
        for (FestivalReservationEntity entity : reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            TemplateInfo info = catalog(entity.getTemplateKey());
            if (info == null) {
                continue; // 配置已下架的模板跳过(登记行保留)
            }
            ReservationListItem item = new ReservationListItem();
            item.setTemplateKey(info.getKey());
            item.setName(info.getName());
            item.setLive(info.isLive());
            item.setFestivalLabel(info.getFestivalLabel());
            item.setFestivalDate(info.getFestivalDate());
            item.setDaysUntil(daysUntilFromToday(info));
            item.setReservedAt(entity.getCreatedAt().toString());
            items.add(item);
        }
        ReservationListResponse response = new ReservationListResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    /** 取消预约(幂等:未预约再取消也成功,恒 deleted=true);templateKey 非枚举值 40000 */
    @Transactional
    public CancelReservationResponse cancel(String templateKey) {
        Long userId = CurrentUser.get();
        try {
            TemplateKey key = TemplateKey.valueOf(templateKey == null ? "" : templateKey.trim());
            reservationRepository.deleteByUserIdAndTemplateKey(userId, key.name());
        } catch (IllegalArgumentException e) {
            throw ApiException.param("请您选择一个模板");
        }
        CancelReservationResponse response = new CancelReservationResponse();
        response.setDeleted(true);
        return response;
    }

    /** 到期清单:festivalDate 落 0-7 天窗口内的模板 + 本人预约标记,按 daysUntil 升序;date 供调度自测 */
    @Transactional(readOnly = true)
    public FestivalDueResponse due(String date) {
        Long userId = CurrentUser.get();
        LocalDate basis = parseBasisDate(date);
        List<FestivalDueItem> items = new ArrayList<>();
        for (TemplateInfo info : TemplateCatalog.list()) {
            if (info.getFestivalDate() == null) {
                continue;
            }
            long daysUntil = ChronoUnit.DAYS.between(basis, LocalDate.parse(info.getFestivalDate()));
            if (daysUntil < 0 || daysUntil > 7) {
                continue;
            }
            FestivalDueItem item = new FestivalDueItem();
            item.setTemplateKey(info.getKey());
            item.setFestivalLabel(info.getFestivalLabel());
            item.setEmoji(extractEmoji(info.getFestivalLabel()));
            item.setFestivalDate(info.getFestivalDate());
            item.setDaysUntil((int) daysUntil);
            item.setLive(info.isLive());
            boolean reservedByMe = findMine(userId, info.getKey()) != null;
            item.setReservedByMe(reservedByMe);
            item.setDueSoon(reservedByMe && daysUntil <= 3);
            items.add(item);
        }
        items.sort(Comparator.comparingInt(FestivalDueItem::getDaysUntil));
        FestivalDueResponse response = new FestivalDueResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    // ---- 内部 ----

    private FestivalReservationEntity findMine(Long userId, TemplateKey templateKey) {
        return reservationRepository.findByUserIdAndTemplateKey(userId, templateKey.name()).orElse(null);
    }

    private FestivalReservation toReservation(TemplateKey templateKey, FestivalReservationEntity entity) {
        FestivalReservation response = new FestivalReservation();
        response.setTemplateKey(templateKey);
        response.setReserved(true);
        if (entity != null) {
            response.setReservedAt(entity.getCreatedAt().toString());
        }
        return response;
    }

    /** POST 口径:不在 TemplateKey 枚举值域即 40400(契约 404,不泄露存在性) */
    private TemplateKey parseKeyOrNotFound(String raw) {
        for (TemplateKey key : TemplateKey.values()) {
            if (key.name().equals(raw)) {
                return key;
            }
        }
        throw ApiException.notFound("没有找到这个模板");
    }

    private TemplateInfo catalog(String templateKey) {
        for (TemplateInfo info : TemplateCatalog.list()) {
            if (info.getKey().name().equals(templateKey)) {
                return info;
            }
        }
        return null;
    }

    private Integer daysUntilFromToday(TemplateInfo info) {
        if (info.getFestivalDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(info.getFestivalDate()));
    }

    private String extractEmoji(String festivalLabel) {
        if (festivalLabel == null) {
            return null;
        }
        Matcher matcher = NON_CHINESE.matcher(festivalLabel);
        if (matcher.find()) {
            String segment = matcher.group().trim();
            return segment.isBlank() ? null : segment;
        }
        return null;
    }

    private LocalDate parseBasisDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw ApiException.param("请您换个日期");
        }
    }
}
