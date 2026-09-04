package com.example.backend.service;

import com.example.backend.common.ApiException;
import com.example.backend.common.CurrentUser;
import com.example.backend.dto.RankDtos.FamilyRankItem;
import com.example.backend.dto.RankDtos.FamilyRankResponse;
import com.example.backend.dto.RankDtos.FriendRankItem;
import com.example.backend.dto.RankDtos.FriendRankResponse;
import com.example.backend.entity.FamilyEntity;
import com.example.backend.entity.FamilyMemberEntity;
import com.example.backend.repository.BlessEventRepository;
import com.example.backend.repository.FamilyMemberRepository;
import com.example.backend.repository.FamilyRepository;
import com.example.backend.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排行榜:家内榜(本周一 0 点起成员福值)/好友家族榜(MVP 以全量家族模拟互关关系,V1.1 接微信好友)。
 * 追赶文案:制造"就差一点反超"的追赶动力,但永不贬低。
 */
@Service
public class RankService {

    private final FamilyMemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final BlessEventRepository blessEventRepository;

    public RankService(FamilyMemberRepository memberRepository,
                       FamilyRepository familyRepository,
                       UserRepository userRepository,
                       BlessEventRepository blessEventRepository) {
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.blessEventRepository = blessEventRepository;
    }

    @Transactional(readOnly = true)
    public FamilyRankResponse getFamilyRank() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = memberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("您还没有加入「家」,请您先创建或从家人邀请卡片进入"));

        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        Map<Long, Integer> weekBless = new HashMap<>();
        for (Object[] row : blessEventRepository.sumBlessByUserSince(
                me.getFamilyId(), weekStart.atStartOfDay())) {
            weekBless.put((Long) row[0], ((Number) row[1]).intValue());
        }

        List<FamilyRankItem> items = new ArrayList<>();
        for (FamilyMemberEntity m : memberRepository.findByFamilyIdOrderByIdAsc(me.getFamilyId())) {
            FamilyRankItem item = new FamilyRankItem();
            item.setUserId(m.getUserId());
            item.setNickname(userRepository.findById(m.getUserId())
                    .map(u -> u.getNickname()).orElse("福友"));
            item.setBless(weekBless.getOrDefault(m.getUserId(), 0));
            item.setStreakDays(m.getStreakDays());
            items.add(item);
        }
        items.sort(Comparator.comparingInt(FamilyRankItem::getBless).reversed());
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }

        FamilyRankResponse resp = new FamilyRankResponse();
        resp.setWeekStart(weekStart.toString());
        resp.setItems(items);
        return resp;
    }

    @Transactional(readOnly = true)
    public FriendRankResponse getFriendFamilyRank() {
        Long userId = CurrentUser.get();
        FamilyMemberEntity me = memberRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("您还没有加入「家」,请您先创建或从家人邀请卡片进入"));

        // MVP:全量家族按福值排序模拟好友关系;V1.1 接微信好友关系过滤
        List<FamilyEntity> families = familyRepository.findAll();
        List<FriendRankItem> items = new ArrayList<>();
        for (FamilyEntity f : families) {
            FriendRankItem item = new FriendRankItem();
            item.setFamilyId(f.getId());
            item.setFamilyName(f.getName());
            item.setBless(f.getTotalBless());
            item.setPlateLevel(f.getPlateLevel());
            item.setMine(f.getId().equals(me.getFamilyId()));
            items.add(item);
        }
        items.sort(Comparator.comparingLong(FriendRankItem::getBless).reversed());
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }

        FriendRankResponse resp = new FriendRankResponse();
        resp.setItems(items);
        resp.setChaseText(buildChaseText(items, me.getFamilyId()));
        return resp;
    }

    /** 追赶文案:落后→就差 N 福值反超;领先→稳住第一;永不贬低 */
    private String buildChaseText(List<FriendRankItem> items, Long myFamilyId) {
        int myIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getFamilyId().equals(myFamilyId)) {
                myIndex = i;
                break;
            }
        }
        if (myIndex < 0) {
            return "接福攒福值,让您的家上榜!";
        }
        if (myIndex == 0) {
            return "稳住!您家现在是第 1 名,全家继续保持!";
        }
        FriendRankItem ahead = items.get(myIndex - 1);
        long gap = ahead.getBless() - items.get(myIndex).getBless();
        return "就差 " + gap + " 福值追上「" + ahead.getFamilyName() + "」,今天全家接福就能反超!";
    }
}
