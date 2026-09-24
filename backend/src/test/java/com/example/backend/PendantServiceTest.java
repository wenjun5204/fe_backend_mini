package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.BaguaDtos.BaguaStatusResponse;
import com.example.backend.dto.MeDtos.PendantWearRequest;
import com.example.backend.dto.MeDtos.PendantWearResponse;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.BaguaService;
import com.example.backend.service.PendantService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 挂件达标解锁制测试:解锁条件从既有数据推导,未达标佩戴返回 40900,不消耗福值 */
@ExtendWith(MockitoExtension.class)
class PendantServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FamilyMemberRepository memberRepository;
    @Mock
    private BaguaService baguaService;

    private PendantService service;

    private static final Long ME = 1L;
    private static final Long FAMILY = 10L;

    @BeforeEach
    void setup() {
        service = new PendantService(userRepository, memberRepository, baguaService);
        CurrentUser.set(ME);
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    private FamilyMemberEntity member(int streakDays, int personalBless) {
        FamilyMemberEntity m = new FamilyMemberEntity();
        m.setUserId(ME);
        m.setFamilyId(FAMILY);
        m.setStreakDays(streakDays);
        m.setPersonalBless(personalBless);
        return m;
    }

    private BaguaStatusResponse bagua(int litCount) {
        BaguaStatusResponse resp = new BaguaStatusResponse();
        resp.setLitCount(litCount);
        return resp;
    }

    private void stubMember(int streakDays, int personalBless, int litCount) {
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(member(streakDays, personalBless)));
        when(baguaService.getStatus()).thenReturn(bagua(litCount));
    }

    private PendantWearRequest wear(String pendant) {
        PendantWearRequest req = new PendantWearRequest();
        req.setPendant(pendant);
        return req;
    }

    @Test
    void 解锁推导口径与设计一致() {
        stubMember(7, 200, 4);
        Set<String> unlocked = service.unlockedPendants(ME);
        // 竹叶默认 + 火焰(连续7天) + 灯笼(福值200) + 八卦(点亮4卦);锦鲤/福包未达标
        assertEquals(Set.of("BAMBOO", "FLAME", "LANTERN", "BAGUA"), unlocked);
    }

    @Test
    void 未加入家族只有新人礼竹叶() {
        when(memberRepository.findByUserId(ME)).thenReturn(Optional.empty());
        assertEquals(Set.of("BAMBOO"), service.unlockedPendants(ME));
    }

    @Test
    void 八卦圆满同时解锁福包() {
        stubMember(30, 2000, 8);
        assertEquals(
                Set.of("BAMBOO", "FLAME", "LANTERN", "KOI", "BAGUA", "FUBAO"),
                service.unlockedPendants(ME));
    }

    @Test
    void 达标佩戴成功并持久化() {
        stubMember(7, 100, 0);
        UserEntity user = new UserEntity();
        user.setId(ME);
        when(userRepository.findById(ME)).thenReturn(Optional.of(user));

        PendantWearResponse resp = service.wear(wear("FLAME"));

        assertEquals("FLAME", resp.getPendant());
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("FLAME", captor.getValue().getPendant());
    }

    @Test
    void 未达标佩戴返回40900不落库() {
        stubMember(1, 100, 0); // 连续 1 天,火焰未解锁
        ApiException e = assertThrows(ApiException.class, () -> service.wear(wear("FLAME")));
        assertEquals("40900", e.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 非法挂件名返回40000() {
        ApiException e = assertThrows(ApiException.class, () -> service.wear(wear("DIAMOND")));
        assertEquals("40000", e.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 佩戴文案不含违禁词() {
        // 老年用户文案护栏:错误提示不得出现算命占卜类词汇
        stubMember(1, 100, 0);
        ApiException e = assertThrows(ApiException.class, () -> service.wear(wear("KOI")));
        String msg = e.getMessage();
        for (String banned : new String[]{"运势", "吉凶", "求签", "算命", "占卜", "改运", "开光", "大师", "卦运"}) {
            assertFalse(msg.contains(banned), "文案不应包含违禁词: " + banned);
        }
        assertTrue(msg.length() <= 24, "错误文案应 ≤24 字");
    }
}
