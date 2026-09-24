package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.FamilyDtos.TargetUserRequest;
import com.example.backend.dto.FortuneDtos.CuifuResult;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.InteractRecordEntity;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.InteractRecordRepository;
import com.example.backend.service.BlessService;
import com.example.backend.service.InteractService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 频控规则测试:添福 1 次/人/对人/天,催福 3 次/人/对人/天 */
@ExtendWith(MockitoExtension.class)
class InteractServiceTest {

    @Mock
    private InteractRecordRepository interactRepository;
    @Mock
    private FamilyMemberRepository memberRepository;
    @Mock
    private BlessService blessService;

    private InteractService service;

    private static final Long ME = 1L;
    private static final Long TARGET = 2L;
    private static final Long FAMILY = 10L;

    @BeforeEach
    void setup() {
        service = new InteractService(interactRepository, memberRepository, blessService);
        CurrentUser.set(ME);

        FamilyMemberEntity myMember = new FamilyMemberEntity();
        myMember.setUserId(ME);
        myMember.setFamilyId(FAMILY);
        lenient().when(memberRepository.findByUserId(ME)).thenReturn(Optional.of(myMember));

        FamilyMemberEntity targetMember = new FamilyMemberEntity();
        targetMember.setUserId(TARGET);
        targetMember.setFamilyId(FAMILY);
        targetMember.setLastDrawDate(null); // 未接今日福
        lenient().when(memberRepository.findByUserId(TARGET)).thenReturn(Optional.of(targetMember));
    }

    @AfterEach
    void cleanup() {
        CurrentUser.clear();
    }

    private TargetUserRequest request(Long targetUserId) {
        TargetUserRequest req = new TargetUserRequest();
        req.setTargetUserId(targetUserId);
        return req;
    }

    @Test
    void 添福超过每日1次返回40300() {
        when(interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                eq(ME), eq(TARGET), any(), eq(InteractRecordEntity.Type.TIANFU)))
                .thenReturn(1L);

        ApiException e = assertThrows(ApiException.class, () -> service.tianfu(request(TARGET)));
        assertEquals("40300", e.getCode());
    }

    @Test
    void 添福首次成功发放8福值() {
        when(interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                eq(ME), eq(TARGET), any(), eq(InteractRecordEntity.Type.TIANFU)))
                .thenReturn(0L);
        when(blessService.grant(eq(ME), eq(FAMILY), any(), eq(TARGET)))
                .thenReturn(new BlessService.GrantResult(8, 100, 500, com.example.backend.dto.FortuneDtos.PlateLevel.BRONZE));

        var result = service.tianfu(request(TARGET));
        assertEquals(8, result.getAmount());
    }

    @Test
    void 催福超过每日3次返回40300() {
        when(interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                eq(ME), eq(TARGET), any(), eq(InteractRecordEntity.Type.CUIFU)))
                .thenReturn(3L);

        ApiException e = assertThrows(ApiException.class, () -> service.cuifu(request(TARGET)));
        assertEquals("40300", e.getCode());
    }

    @Test
    void 催福第三次后剩余次数为0() {
        when(interactRepository.countByUserIdAndTargetUserIdAndDateAndType(
                eq(ME), eq(TARGET), any(), eq(InteractRecordEntity.Type.CUIFU)))
                .thenReturn(2L);

        CuifuResult result = service.cuifu(request(TARGET));
        assertEquals(0, result.getRemainCount());
    }

    @Test
    void 不能给自己添福() {
        ApiException e = assertThrows(ApiException.class, () -> service.tianfu(request(ME)));
        assertEquals("40000", e.getCode());
    }

    @Test
    void 催福目标今日已接福返回40900() {
        FamilyMemberEntity target = new FamilyMemberEntity();
        target.setUserId(TARGET);
        target.setFamilyId(FAMILY);
        target.setLastDrawDate(LocalDate.now());
        when(memberRepository.findByUserId(TARGET)).thenReturn(Optional.of(target));

        ApiException e = assertThrows(ApiException.class, () -> service.cuifu(request(TARGET)));
        assertEquals("40900", e.getCode());
    }

    @Test
    void 非同一家族成员返回40400() {
        when(memberRepository.findByUserId(99L)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.tianfu(request(99L)));
        assertEquals("40400", e.getCode());
    }
}
