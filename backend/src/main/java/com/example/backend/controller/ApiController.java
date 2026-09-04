package com.example.backend.controller;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.dto.FamilyDtos.CreateFamilyRequest;
import com.example.backend.dto.FamilyDtos.FamilyDetail;
import com.example.backend.dto.FamilyDtos.InviteInfo;
import com.example.backend.dto.FamilyDtos.JoinFamilyRequest;
import com.example.backend.dto.FamilyDtos.TargetUserRequest;
import com.example.backend.dto.FortuneDtos.BlessResult;
import com.example.backend.dto.FortuneDtos.CuifuResult;
import com.example.backend.dto.FortuneDtos.FortuneDrawResponse;
import com.example.backend.dto.FortuneDtos.FortuneTodayResponse;
import com.example.backend.dto.RankDtos.FamilyRankResponse;
import com.example.backend.dto.RankDtos.FriendRankResponse;
import com.example.backend.service.AuthService;
import com.example.backend.service.FamilyService;
import com.example.backend.service.FortuneService;
import com.example.backend.service.InteractService;
import com.example.backend.service.RankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 契约路由(与 api/openapi.yaml 一一对应,13 个 operationId) */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService authService;
    private final FamilyService familyService;
    private final FortuneService fortuneService;
    private final InteractService interactService;
    private final RankService rankService;

    public ApiController(AuthService authService,
                         FamilyService familyService,
                         FortuneService fortuneService,
                         InteractService interactService,
                         RankService rankService) {
        this.authService = authService;
        this.familyService = familyService;
        this.fortuneService = fortuneService;
        this.interactService = interactService;
        this.rankService = rankService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/family")
    public FamilyDetail getMyFamily() {
        return familyService.getMyFamily();
    }

    @PostMapping("/family")
    public FamilyDetail createFamily(@RequestBody CreateFamilyRequest request) {
        return familyService.createFamily(request);
    }

    @PostMapping("/family/join")
    public FamilyDetail joinFamily(@RequestBody JoinFamilyRequest request) {
        return familyService.joinFamily(request);
    }

    @GetMapping("/fortune/today")
    public FortuneTodayResponse getTodayFortune() {
        return fortuneService.getTodayFortune();
    }

    @PostMapping("/fortune/draw")
    public FortuneDrawResponse drawFortune() {
        return fortuneService.drawFortune();
    }

    @PostMapping("/fortune/share")
    public BlessResult shareFortune() {
        return fortuneService.shareFortune();
    }

    @PostMapping("/fortune/task-done")
    public BlessResult completeFortuneTask() {
        return fortuneService.completeFortuneTask();
    }

    @PostMapping("/interact/tianfu")
    public BlessResult tianfu(@RequestBody TargetUserRequest request) {
        return interactService.tianfu(request);
    }

    @PostMapping("/interact/cuifu")
    public CuifuResult cuifu(@RequestBody TargetUserRequest request) {
        return interactService.cuifu(request);
    }

    @GetMapping("/rank/family")
    public FamilyRankResponse getFamilyRank() {
        return rankService.getFamilyRank();
    }

    @GetMapping("/rank/friends")
    public FriendRankResponse getFriendFamilyRank() {
        return rankService.getFriendFamilyRank();
    }

    @GetMapping("/invite/info")
    public InviteInfo getInviteInfo(@RequestParam("inviteCode") String inviteCode) {
        return familyService.getInviteInfo(inviteCode);
    }
}
