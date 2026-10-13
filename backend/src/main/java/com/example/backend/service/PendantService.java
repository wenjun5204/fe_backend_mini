package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BaguaDtos.BaguaStatusResponse;
import com.example.backend.dto.MeDtos.PendantWearRequest;
import com.example.backend.dto.MeDtos.PendantWearResponse;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.UserRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 头像挂件:达标解锁制(不消耗福值,与「福值只涨不跌」一致),佩戴持久化于用户档案,家人可见。
 * 解锁条件(全部由既有数据推导):
 * 竹叶 BAMBOO=默认 / 火焰 FLAME=连续接福 7 天 / 灯笼 LANTERN=个人福值≥200 /
 * 锦鲤 KOI=个人福值≥1000 / 八卦 BAGUA=点亮 4 卦 / 福包 FUBAO=八卦圆满。
 */
@Service
public class PendantService {

    private static final Set<String> PENDANTS = Set.of(
            "BAMBOO", "FLAME", "LANTERN", "KOI", "BAGUA", "FUBAO");

    private final UserRepository userRepository;
    private final FamilyMemberRepository memberRepository;
    private final BaguaService baguaService;

    public PendantService(UserRepository userRepository,
                          FamilyMemberRepository memberRepository,
                          BaguaService baguaService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.baguaService = baguaService;
    }

    /** 佩戴挂件:未达标返回 40900(服务端校验,不信客户端解锁态)。 */
    @Transactional
    public PendantWearResponse wear(PendantWearRequest request) {
        Long userId = CurrentUser.get();
        String pendant = request.getPendant();
        if (pendant == null || !PENDANTS.contains(pendant)) {
            throw ApiException.param("没有这个挂件,请您重新选一选");
        }
        if (!unlockedPendants(userId).contains(pendant)) {
            throw ApiException.conflict("这个挂件还没到点亮的时候,再陪家人攒一攒");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.param("请您先登录"));
        user.setPendant(pendant);
        userRepository.save(user);

        PendantWearResponse resp = new PendantWearResponse();
        resp.setPendant(pendant);
        return resp;
    }

    /** 当前用户已解锁的挂件集合(供佩戴页展示;纯推导不落表)。 */
    @Transactional
    public Set<String> unlockedPendants(Long userId) {
        Set<String> unlocked = new java.util.HashSet<>();
        unlocked.add("BAMBOO"); // 新人礼,默认解锁
        FamilyMemberEntity member = memberRepository.findByUserId(userId).orElse(null);
        if (member != null) {
            if (member.getStreakDays() >= 7) {
                unlocked.add("FLAME");
            }
            if (member.getPersonalBless() >= 200) {
                unlocked.add("LANTERN");
            }
            if (member.getPersonalBless() >= 1000) {
                unlocked.add("KOI");
            }
            BaguaStatusResponse bagua = baguaService.getStatus();
            if (bagua.getLitCount() >= 4) {
                unlocked.add("BAGUA");
            }
            if (bagua.getLitCount() >= 8) {
                unlocked.add("FUBAO");
            }
        }
        return unlocked;
    }
}
