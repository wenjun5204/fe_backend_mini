package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.FamilyDtos.CreateFamilyRequest;
import com.example.backend.dto.FamilyDtos.FamilyDetail;
import com.example.backend.dto.FamilyDtos.InviteInfo;
import com.example.backend.dto.FamilyDtos.JoinFamilyRequest;
import com.example.backend.dto.FamilyDtos.MemberStatus;
import com.example.backend.dto.FortuneDtos.PlateLevel;
import com.example.backend.entity.BlessEventEntity;
import com.example.backend.entity.FamilyEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.entity.UserEntity;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyRepository;
import com.example.backend.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 家族:建家/进家/我的家。规则:成员上限 8,每人限加入 1 个,无踢人,仅户主可解散 */
@Service
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final BlessService blessService;
    private final SecureRandom random = new SecureRandom();

    public FamilyService(FamilyRepository familyRepository,
                         FamilyMemberRepository familyMemberRepository,
                         UserRepository userRepository,
                         BlessService blessService) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.blessService = blessService;
    }

    @Transactional
    public FamilyDetail createFamily(CreateFamilyRequest request) {
        Long userId = CurrentUser.get();
        String name = request == null || request.getName() == null || request.getName().isBlank()
                ? defaultFamilyName(userId)
                : request.getName().trim();
        if (name.length() < 1 || name.length() > 10) {
            throw ApiException.param("家族名要 1 到 10 个字哦");
        }
        if (familyMemberRepository.findByUserId(userId).isPresent()) {
            throw ApiException.conflict("您已经在「家」里啦,一个家人只能有一个家");
        }

        FamilyEntity family = new FamilyEntity();
        family.setName(name);
        family.setInviteCode(generateInviteCode());
        family.setOwnerUserId(userId);
        family.setTotalBless(0L);
        family.setPlateLevel(PlateLevel.NONE);
        family = familyRepository.save(family);

        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberEntity.Role.OWNER);
        familyMemberRepository.save(member);

        return buildDetail(userId, family);
    }

    @Transactional
    public FamilyDetail joinFamily(JoinFamilyRequest request) {
        Long userId = CurrentUser.get();
        if (request == null || request.getInviteCode() == null || request.getInviteCode().isBlank()) {
            throw ApiException.param("请您从家人的邀请卡片进入");
        }
        if (familyMemberRepository.findByUserId(userId).isPresent()) {
            throw ApiException.conflict("您已经在「家」里啦,一个家人只能有一个家");
        }
        FamilyEntity family = familyRepository.findByInviteCode(request.getInviteCode().trim())
                .orElseThrow(() -> ApiException.notFound("没有找到这个家,请您确认邀请卡片"));
        if (familyMemberRepository.countByFamilyId(family.getId()) >= FamilyEntity.MEMBER_LIMIT) {
            throw ApiException.conflict("这个家已经满员啦(最多 8 人)");
        }

        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(FamilyMemberEntity.Role.MEMBER);
        familyMemberRepository.save(member);

        // 邀请奖励:邀请人 +30
        FamilyMemberEntity inviter = familyMemberRepository.findByFamilyIdOrderByIdAsc(family.getId()).stream()
                .filter(m -> !m.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        if (inviter != null) {
            blessService.grant(inviter.getUserId(), family.getId(), BlessEventEntity.Type.INVITE_JOIN, null);
        }

        return buildDetail(userId, family);
    }

    @Transactional(readOnly = true)
    public FamilyDetail getMyFamily() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity member = familyMemberRepository.findByUserId(userId).orElse(null);
        FamilyDetail detail = new FamilyDetail();
        if (member == null) {
            detail.setNotJoined(true);
            detail.setMembers(List.of());
            return detail;
        }
        FamilyEntity family = familyRepository.findById(member.getFamilyId())
                .orElseThrow(() -> ApiException.notFound("没有找到这个家"));
        return buildDetail(userId, family);
    }

    @Transactional(readOnly = true)
    public InviteInfo getInviteInfo(String inviteCode) {
        FamilyEntity family = familyRepository.findByInviteCode(inviteCode == null ? "" : inviteCode.trim())
                .orElseThrow(() -> ApiException.notFound("没有找到这个家,请您确认邀请卡片"));
        InviteInfo info = new InviteInfo();
        info.setFamilyId(family.getId());
        info.setFamilyName(family.getName());
        info.setMemberCount((int) familyMemberRepository.countByFamilyId(family.getId()));
        info.setInviteText(family.getName() + "邀请您一起接福气，全家福值榜见！");
        return info;
    }

    private FamilyDetail buildDetail(Long userId, FamilyEntity family) {
        FamilyDetail detail = new FamilyDetail();
        detail.setNotJoined(false);
        detail.setFamilyId(family.getId());
        detail.setFamilyName(family.getName());
        detail.setInviteCode(family.getInviteCode());
        detail.setTotalBless(family.getTotalBless());
        detail.setPlateLevel(family.getPlateLevel());
        detail.setNextPlateBless(BlessService.nextPlateGap(family.getTotalBless()));

        List<MemberStatus> members = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (FamilyMemberEntity m : familyMemberRepository.findByFamilyIdOrderByIdAsc(family.getId())) {
            UserEntity user = userRepository.findById(m.getUserId()).orElse(null);
            MemberStatus status = new MemberStatus();
            status.setUserId(m.getUserId());
            status.setNickname(user != null ? user.getNickname() : "福友");
            status.setAvatarUrl(user != null ? user.getAvatarUrl() : null);
            status.setRemark(m.getRemark());
            status.setRole(m.getRole().name());
            status.setPersonalBless(m.getPersonalBless());
            status.setStreakDays(m.getStreakDays());
            status.setDrawnToday(today.equals(m.getLastDrawDate()));
            members.add(status);
        }
        detail.setMembers(members);
        return detail;
    }

    private String defaultFamilyName(Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        String nickname = user != null && user.getNickname() != null ? user.getNickname() : "福友";
        return (nickname.length() > 2 ? nickname.substring(0, 2) : nickname) + "家";
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        do {
            sb.setLength(0);
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
        } while (familyRepository.existsByInviteCode(sb.toString()));
        return sb.toString();
    }
}
