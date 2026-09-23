package com.example.backend.controller;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.dto.BaguaDtos.BaguaCelebrateResponse;
import com.example.backend.dto.BaguaDtos.BaguaStatusResponse;
import com.example.backend.dto.MeDtos.PendantWearRequest;
import com.example.backend.dto.MeDtos.PendantWearResponse;
import com.example.backend.dto.BirthdayDtos.BirthdayListResponse;
import com.example.backend.dto.BirthdayDtos.BirthdayRecord;
import com.example.backend.dto.BirthdayDtos.BirthdayReminder;
import com.example.backend.dto.BirthdayDtos.BirthdayReminderRequest;
import com.example.backend.dto.BirthdayDtos.BirthdayUpsertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarConvertRequest;
import com.example.backend.dto.BirthdayDtos.CalendarConvertResponse;
import com.example.backend.dto.BirthdayDtos.DeleteBirthdayResponse;
import com.example.backend.dto.BirthdayDtos.UpcomingBirthdayResponse;
import com.example.backend.dto.FamilyDtos.CreateFamilyRequest;
import com.example.backend.dto.FamilyDtos.FamilyDetail;
import com.example.backend.dto.FamilyDtos.InviteInfo;
import com.example.backend.dto.FamilyDtos.JoinFamilyRequest;
import com.example.backend.dto.FamilyDtos.TargetUserRequest;
import com.example.backend.dto.FortuneDtos.BlessResult;
import com.example.backend.dto.FortuneDtos.CuifuResult;
import com.example.backend.dto.FortuneDtos.FortuneDrawResponse;
import com.example.backend.dto.FortuneDtos.FortuneTaskDoneRequest;
import com.example.backend.dto.FortuneDtos.FortuneTodayResponse;
import com.example.backend.dto.JieyouDtos.JieyouQuoteResponse;
import com.example.backend.dto.NotifyDtos.NotifyStatusResponse;
import com.example.backend.dto.NotifyDtos.NotifySubscribeRequest;
import com.example.backend.dto.NotifyDtos.NotifySubscribeResponse;
import com.example.backend.dto.RankDtos.FamilyRankResponse;
import com.example.backend.dto.RankDtos.FriendRankResponse;
import com.example.backend.service.AuthService;
import com.example.backend.service.BaguaService;
import com.example.backend.service.BirthdayService;
import com.example.backend.service.FamilyService;
import com.example.backend.service.FortuneService;
import com.example.backend.service.InteractService;
import com.example.backend.service.JieyouService;
import com.example.backend.service.NotifyService;
import com.example.backend.service.PendantService;
import com.example.backend.service.RankService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 契约路由(与 api/openapi.yaml 一一对应,25 个 operationId;/api/ping 为部署探活,不入契约) */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService authService;
    private final BaguaService baguaService;
    private final BirthdayService birthdayService;
    private final FamilyService familyService;
    private final FortuneService fortuneService;
    private final InteractService interactService;
    private final JieyouService jieyouService;
    private final NotifyService notifyService;
    private final PendantService pendantService;
    private final RankService rankService;

    public ApiController(AuthService authService,
                         BaguaService baguaService,
                         BirthdayService birthdayService,
                         FamilyService familyService,
                         FortuneService fortuneService,
                         InteractService interactService,
                         JieyouService jieyouService,
                         NotifyService notifyService,
                         PendantService pendantService,
                         RankService rankService) {
        this.authService = authService;
        this.baguaService = baguaService;
        this.birthdayService = birthdayService;
        this.familyService = familyService;
        this.fortuneService = fortuneService;
        this.interactService = interactService;
        this.jieyouService = jieyouService;
        this.notifyService = notifyService;
        this.pendantService = pendantService;
        this.rankService = rankService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    /** 云托管/负载均衡探活端点(免鉴权,不入契约,仅部署健康检查) */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
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
    public BlessResult completeFortuneTask(@RequestBody(required = false) FortuneTaskDoneRequest request) {
        return fortuneService.completeFortuneTask(request);
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

    @PostMapping("/calendar/convert")
    public CalendarConvertResponse convertCalendar(@RequestBody CalendarConvertRequest request) {
        return birthdayService.convert(request);
    }

    @GetMapping("/birthdays")
    public BirthdayListResponse getBirthdays() {
        return birthdayService.getBirthdays();
    }

    @PostMapping("/birthdays")
    public BirthdayRecord createBirthday(@RequestBody BirthdayUpsertRequest request) {
        return birthdayService.create(request);
    }

    @GetMapping("/birthdays/upcoming")
    public UpcomingBirthdayResponse getUpcomingBirthday() {
        return birthdayService.getUpcomingBirthday();
    }

    @PutMapping("/birthdays/{id}")
    public BirthdayRecord updateBirthday(@PathVariable Long id, @RequestBody BirthdayUpsertRequest request) {
        return birthdayService.update(id, request);
    }

    @DeleteMapping("/birthdays/{id}")
    public DeleteBirthdayResponse deleteBirthday(@PathVariable Long id) {
        return birthdayService.delete(id);
    }

    @PutMapping("/birthdays/{id}/reminder")
    public BirthdayReminder updateBirthdayReminder(@PathVariable Long id,
                                                   @RequestBody BirthdayReminderRequest request) {
        return birthdayService.updateReminder(id, request);
    }

    @GetMapping("/bagua/status")
    public BaguaStatusResponse getBaguaStatus() {
        return baguaService.getStatus();
    }

    @PostMapping("/bagua/celebrate")
    public BaguaCelebrateResponse celebrateBaguaComplete() {
        return baguaService.celebrate();
    }

    @GetMapping("/jieyou/quote")
    public JieyouQuoteResponse getJieyouQuote() {
        return jieyouService.getQuote();
    }

    @PostMapping("/me/pendant")
    public PendantWearResponse wearPendant(@RequestBody PendantWearRequest request) {
        return pendantService.wear(request);
    }

    @PostMapping("/notify/subscribe")
    public NotifySubscribeResponse registerNotifySubscription(@RequestBody NotifySubscribeRequest request) {
        return notifyService.subscribe(request);
    }

    @GetMapping("/notify/status")
    public NotifyStatusResponse getNotifyStatus() {
        return notifyService.getStatus();
    }
}
