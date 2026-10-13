package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.DayDtos.DayKind;
import com.example.backend.dto.DayDtos.DayVisibility;
import com.example.backend.dto.MomentDtos.DayCollectedMomentItem;
import com.example.backend.dto.MomentDtos.DayCollectedMomentsResponse;
import com.example.backend.dto.MomentDtos.DeleteMomentResponse;
import com.example.backend.dto.MomentDtos.FamilyMomentView;
import com.example.backend.dto.MomentDtos.MomentCollectRequest;
import com.example.backend.dto.MomentDtos.MomentCollectResponse;
import com.example.backend.dto.MomentDtos.MomentCreateRequest;
import com.example.backend.dto.MomentDtos.MomentLikeResponse;
import com.example.backend.dto.MomentDtos.MomentListResponse;
import com.example.backend.dto.PhotoDtos.PhotoScene;
import com.example.backend.entity.DayRecordEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.FamilyMomentEntity;
import com.example.backend.entity.FamilyMomentEntity.MomentStatus;
import com.example.backend.entity.MomentCollectEntity;
import com.example.backend.entity.MomentLikeEntity;
import com.example.backend.entity.PhotoAssetEntity;
import com.example.backend.entity.PhotoAssetEntity.PhotoStatus;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.DayRecordRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyMomentRepository;
import com.example.backend.repository.MomentCollectRepository;
import com.example.backend.repository.MomentLikeRepository;
import com.example.backend.repository.PhotoAssetRepository;
import com.example.backend.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 念家时·家人瞬间:照片+一句话家族时间线、真好(限 1 次)、收藏进日子(生日福卡素材池)。
 * 隐私权红线:删除仅认 userId 不认角色(户主/家人均 40400 不泄露存在性);
 * 瞬间全线要求家族(未加入 40900;时间线降级空数组);readOnly 零写库。
 * 时间线互动态批量聚合(4 条附加查询撑满整页,防 N+1);素材池服务端解析昵称(契约各异)。
 */
@Service
public class MomentService {

    private static final int DAILY_LIMIT = 10;
    private static final int MAX_TEXT_CHARS = 60;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final String MOMENT_NOT_FOUND = "没有找到这条瞬间";
    private static final String DAY_NOT_FOUND = "没有找到这条日子";

    private final FamilyMomentRepository momentRepository;
    private final MomentLikeRepository likeRepository;
    private final MomentCollectRepository collectRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DayRecordRepository dayRecordRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final UserRepository userRepository;

    public MomentService(FamilyMomentRepository momentRepository,
                          MomentLikeRepository likeRepository,
                          MomentCollectRepository collectRepository,
                          FamilyMemberRepository familyMemberRepository,
                          DayRecordRepository dayRecordRepository,
                          PhotoAssetRepository photoAssetRepository,
                          UserRepository userRepository) {
        this.momentRepository = momentRepository;
        this.likeRepository = likeRepository;
        this.collectRepository = collectRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.dayRecordRepository = dayRecordRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.userRepository = userRepository;
    }

    // ---- 发布 ----

    /** 未加入家族 40900;photoId 三重校验(本人+scene=MOMENT+ORPHAN);同事务绑定照片 ORPHAN→ACTIVE */
    @Transactional
    public FamilyMomentView create(MomentCreateRequest request) {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = familyMemberRepository.findByUserId(userId).orElse(null);
        if (me == null) {
            throw ApiException.conflict("先创建或加入一个家，再记录家人瞬间");
        }
        if (request == null || request.getPhotoId() == null || request.getPhotoId().isBlank()) {
            throw ApiException.param("请先选一张照片");
        }
        PhotoAssetEntity photo = photoAssetRepository
                .findByPhotoId(request.getPhotoId().trim()).orElse(null);
        if (photo == null || !photo.getUserId().equals(userId)
                || photo.getScene() != PhotoScene.MOMENT
                || photo.getStatus() != PhotoStatus.ORPHAN) {
            throw ApiException.param("这张照片不能用，请重新选择");
        }
        String text = request.getText() == null ? "" : request.getText().trim();
        if (text.length() < 1 || text.length() > MAX_TEXT_CHARS) {
            throw ApiException.param("瞬间的话要在 1 到 60 字之间");
        }
        if (DayTextLib.FORBIDDEN_WORDS.stream().anyMatch(text::contains)) {
            throw ApiException.param("这句话里有不合适的词，麻烦改一改");
        }
        // 日限 10 条:实体表当日行数即额度账本,含软删行(防删了再发洗额度)
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        if (momentRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= DAILY_LIMIT) {
            throw ApiException.rateLimited("今天发了 10 条瞬间，明天再记录");
        }
        FamilyMomentEntity moment = new FamilyMomentEntity();
        moment.setFamilyId(me.getFamilyId());
        moment.setUserId(userId);
        moment.setPhotoId(photo.getPhotoId());
        moment.setText(text);
        moment.setStatus(MomentStatus.ACTIVE);
        moment.setCreatedAt(LocalDateTime.now());
        momentRepository.save(moment);
        // 创建即绑定:照片 ORPHAN→ACTIVE 并回写 moment_id(同事务)
        photo.setStatus(PhotoStatus.ACTIVE);
        photo.setMomentId(moment.getId());
        photoAssetRepository.save(photo);
        FamilyMomentView view = toView(moment, 0, false, 0, List.of());
        return view;
    }

    // ---- 时间线 ----

    /** 本家族全部 ACTIVE 瞬间,id 倒序 cursor 分页;无家族空数组(不报错,前端空状态) */
    @Transactional(readOnly = true)
    public MomentListResponse list(Long beforeId, Integer limit) {
        Long userId = CurrentUser.get();
        Long familyId = familyMemberRepository.findByUserId(userId)
                .map(FamilyMemberEntity::getFamilyId)
                .orElse(null);
        MomentListResponse response = new MomentListResponse();
        if (familyId == null) {
            response.setItems(List.of());
            return response;
        }
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.param("每页条数要在 1 到 20 之间");
        }
        if (beforeId != null && beforeId < 0) {
            throw ApiException.param("游标不合法");
        }
        // beforeId 缺省/0=从首页开始:id 自 1 起,0 用 MAX_VALUE 哨兵使 id < 哨兵恒真
        long cursor = beforeId == null || beforeId == 0 ? Long.MAX_VALUE : beforeId;
        // 取 limit+1 条:多出的 1 条即「还有更早」的判据
        List<FamilyMomentEntity> page = momentRepository
                .findByFamilyIdAndStatusAndIdLessThanOrderByIdDesc(
                        familyId, MomentStatus.ACTIVE, cursor,
                        org.springframework.data.domain.PageRequest.of(0, size + 1));
        boolean hasMore = page.size() > size;
        List<FamilyMomentEntity> visible = page.subList(0, Math.min(size, page.size()));
        response.setItems(toViews(visible, userId));
        if (hasMore) {
            response.setNextCursor(visible.get(visible.size() - 1).getId());
        }
        return response;
    }

    /** 批量聚合互动态:likeCount/collectedCount 各一条 group by、likedByMe/collectedByMeDayIds 各一条——整页 4 条附加查询 */
    private List<FamilyMomentView> toViews(List<FamilyMomentEntity> moments, Long userId) {
        List<Long> ids = moments.stream().map(FamilyMomentEntity::getId).toList();
        Map<Long, Integer> likeCounts = countsById(likeRepository.countByMomentIdInGroupByMomentId(ids));
        Map<Long, Integer> collectCounts = countsById(
                collectRepository.countByMomentIdInGroupByMomentId(ids));
        Set<Long> likedMomentIds = new HashSet<>();
        Map<Long, List<Long>> collectedDayIdsByMoment = new HashMap<>();
        if (!ids.isEmpty()) {
            for (MomentLikeEntity like : likeRepository.findByUserIdAndMomentIdIn(userId, ids)) {
                likedMomentIds.add(like.getMomentId());
            }
            for (MomentCollectEntity collect : collectRepository.findByUserIdAndMomentIdIn(userId, ids)) {
                collectedDayIdsByMoment.computeIfAbsent(collect.getMomentId(), k -> new ArrayList<>())
                        .add(collect.getDayId());
            }
        }
        return moments.stream()
                .map(moment -> toView(moment,
                        likeCounts.getOrDefault(moment.getId(), 0),
                        likedMomentIds.contains(moment.getId()),
                        collectCounts.getOrDefault(moment.getId(), 0),
                        collectedDayIdsByMoment.getOrDefault(moment.getId(), List.of())))
                .toList();
    }

    private Map<Long, Integer> countsById(List<Object[]> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private FamilyMomentView toView(FamilyMomentEntity moment, int likeCount, boolean likedByMe,
                                     int collectedCount, List<Long> collectedByMeDayIds) {
        FamilyMomentView view = new FamilyMomentView();
        view.setId(moment.getId());
        view.setUserId(moment.getUserId());
        view.setPhotoId(moment.getPhotoId());
        view.setPhotoUrl("/api/photos/" + moment.getPhotoId());
        view.setText(moment.getText());
        view.setCreatedAt(moment.getCreatedAt().toString());
        view.setLikeCount(likeCount);
        view.setLikedByMe(likedByMe);
        view.setCollectedCount(collectedCount);
        view.setCollectedByMeDayIds(collectedByMeDayIds);
        return view;
    }

    // ---- 删除(隐私权红线) ----

    /** 仅本人可删(校验只认 userId 不认角色);幂等;软删+联动照片 DELETED;like/collect 行保留 */
    @Transactional
    public DeleteMomentResponse delete(Long id) {
        Long userId = CurrentUser.get();
        FamilyMomentEntity moment = momentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(MOMENT_NOT_FOUND));
        if (!moment.getUserId().equals(userId)) {
            throw ApiException.notFound(MOMENT_NOT_FOUND);
        }
        if (moment.getStatus() == MomentStatus.ACTIVE) {
            moment.setStatus(MomentStatus.DELETED);
            momentRepository.save(moment);
            photoAssetRepository.findByPhotoId(moment.getPhotoId()).ifPresent(photo -> {
                photo.setStatus(PhotoStatus.DELETED);
                photoAssetRepository.save(photo);
            });
        }
        DeleteMomentResponse response = new DeleteMomentResponse();
        response.setDeleted(true);
        return response;
    }

    // ---- 真好 ----

    /** 本家族 ACTIVE 瞬间才可点(跨家族/不存在 40400 同文案);upsert 幂等不增数 */
    @Transactional
    public MomentLikeResponse like(Long id) {
        requireFamilyMomentVisible(id);
        Long userId = CurrentUser.get();
        if (likeRepository.findByMomentIdAndUserId(id, userId).isEmpty()) {
            MomentLikeEntity like = new MomentLikeEntity();
            like.setMomentId(id);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            try {
                likeRepository.saveAndFlush(like);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 并发撞 uk(moment_id,user_id):幂等回读
            }
        }
        return likeResponse(id, true);
    }

    /** 取消真好:删行幂等(未点过再取消也成功) */
    @Transactional
    public MomentLikeResponse unlike(Long id) {
        requireFamilyMomentVisible(id);
        likeRepository.deleteByMomentIdAndUserId(id, CurrentUser.get());
        return likeResponse(id, false);
    }

    private MomentLikeResponse likeResponse(Long momentId, boolean likedByMe) {
        MomentLikeResponse response = new MomentLikeResponse();
        response.setLikeCount((int) likeRepository.countByMomentId(momentId));
        response.setLikedByMe(likedByMe);
        return response;
    }

    // ---- 收藏进日子 ----

    /** dayId 须本人可见的 BIRTHDAY 日子(非 BIRTHDAY 40000/不可见 40400);upsert 幂等;家人可共享素材 */
    @Transactional
    public MomentCollectResponse collect(Long id, MomentCollectRequest request) {
        Long userId = CurrentUser.get();
        FamilyMomentEntity moment = requireFamilyMomentVisible(id);
        Long familyId = familyMemberRepository.findByUserId(userId)
                .map(FamilyMemberEntity::getFamilyId).orElse(null);
        if (request == null || request.getDayId() == null) {
            throw ApiException.param("请先选择一个日子");
        }
        DayRecordEntity record = dayRecordRepository.findById(request.getDayId()).orElse(null);
        if (record == null || !canView(record, userId, familyId)) {
            throw ApiException.notFound(DAY_NOT_FOUND);
        }
        if (record.getKind() != DayKind.BIRTHDAY) {
            throw ApiException.param("生日福卡的素材，要收藏到生日日子里");
        }
        if (collectRepository.findByMomentIdAndDayIdAndUserId(id, request.getDayId(), userId).isEmpty()) {
            MomentCollectEntity collect = new MomentCollectEntity();
            collect.setMomentId(moment.getId());
            collect.setDayId(record.getId());
            collect.setUserId(userId);
            collect.setCreatedAt(LocalDateTime.now());
            try {
                collectRepository.saveAndFlush(collect);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 并发撞 uk(moment_id,day_id,user_id):幂等回读
            }
        }
        return collectResponse(id, true, record.getId());
    }

    /** 取消收藏(query dayId,幂等);取消后素材池与 collectedCount 即时反映 */
    @Transactional
    public MomentCollectResponse uncollect(Long id, Long dayId) {
        if (dayId == null || dayId <= 0) {
            throw ApiException.param("请先选择一个日子");
        }
        requireFamilyMomentVisible(id);
        collectRepository.deleteByMomentIdAndDayIdAndUserId(id, dayId, CurrentUser.get());
        return collectResponse(id, false, dayId);
    }

    private MomentCollectResponse collectResponse(Long momentId, boolean collectedByMe, Long dayId) {
        MomentCollectResponse response = new MomentCollectResponse();
        response.setCollectedCount((int) collectRepository.countByMomentId(momentId));
        response.setCollectedByMe(collectedByMe);
        response.setDayId(dayId);
        return response;
    }

    // ---- 素材池 ----

    /** 生日福卡素材数据源:全家人收藏到该日子的瞬间(collectAt 倒序);服务端解析昵称;已删瞬间条目即时消失 */
    @Transactional(readOnly = true)
    public DayCollectedMomentsResponse listDayCollected(Long dayId) {
        Long userId = CurrentUser.get();
        Long familyId = familyMemberRepository.findByUserId(userId)
                .map(FamilyMemberEntity::getFamilyId).orElse(null);
        DayRecordEntity record = dayRecordRepository.findById(dayId)
                .orElseThrow(() -> ApiException.notFound(DAY_NOT_FOUND));
        if (!canView(record, userId, familyId)) {
            throw ApiException.notFound(DAY_NOT_FOUND);
        }
        if (record.getKind() != DayKind.BIRTHDAY) {
            throw ApiException.param("生日福卡的素材，要收藏到生日日子里");
        }
        List<MomentCollectEntity> collects = collectRepository.findByDayIdOrderByIdDesc(dayId);
        List<DayCollectedMomentItem> items = new ArrayList<>();
        Set<Long> nicknameIds = new HashSet<>();
        for (MomentCollectEntity collect : collects) {
            FamilyMomentEntity moment = momentRepository.findById(collect.getMomentId()).orElse(null);
            if (moment == null || moment.getStatus() != MomentStatus.ACTIVE) {
                continue;
            }
            nicknameIds.add(moment.getUserId());
            nicknameIds.add(collect.getUserId());
        }
        Map<Long, String> nicknames = nicknames(nicknameIds);
        for (MomentCollectEntity collect : collects) {
            FamilyMomentEntity moment = momentRepository.findById(collect.getMomentId()).orElse(null);
            if (moment == null || moment.getStatus() != MomentStatus.ACTIVE) {
                continue;
            }
            DayCollectedMomentItem item = new DayCollectedMomentItem();
            item.setMomentId(moment.getId());
            item.setPhotoUrl("/api/photos/" + moment.getPhotoId());
            item.setText(moment.getText());
            item.setAuthorName(nicknames.getOrDefault(moment.getUserId(), "家人"));
            item.setCollectedBy(nicknames.getOrDefault(collect.getUserId(), "家人"));
            item.setCollectedAt(collect.getCreatedAt().toString());
            items.add(item);
        }
        DayCollectedMomentsResponse response = new DayCollectedMomentsResponse();
        response.setItems(items);
        response.setTotal(items.size());
        return response;
    }

    // ---- 内部 ----

    /** 瞬间须 ACTIVE 且属本人家族(跨家族/不存在/已删统一 40400 同文案,不泄露存在性) */
    private FamilyMomentEntity requireFamilyMomentVisible(Long id) {
        Long userId = CurrentUser.get();
        Long familyId = familyMemberRepository.findByUserId(userId)
                .map(FamilyMemberEntity::getFamilyId).orElse(null);
        FamilyMomentEntity moment = momentRepository.findById(id).orElse(null);
        if (moment == null || moment.getStatus() != MomentStatus.ACTIVE
                || familyId == null || !familyId.equals(moment.getFamilyId())) {
            throw ApiException.notFound(MOMENT_NOT_FOUND);
        }
        return moment;
    }

    /** 本人可见口径与 days 同源:本人创建 OR 同家族 FAMILY */
    private boolean canView(DayRecordEntity record, Long userId, Long familyId) {
        return record.getUserId().equals(userId)
                || (familyId != null && familyId.equals(record.getFamilyId())
                    && record.getVisibility() == DayVisibility.FAMILY);
    }

    private Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> nicknames = new HashMap<>();
        for (UserEntity user : userRepository.findAllById(userIds)) {
            nicknames.put(user.getId(), user.getNickname());
        }
        return nicknames;
    }
}
