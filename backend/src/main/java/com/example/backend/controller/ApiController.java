package com.example.backend.controller;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.LoginResponse;
import com.example.backend.dto.BaguaDtos.BaguaCelebrateResponse;
import com.example.backend.dto.BaguaDtos.BaguaStatusResponse;
import com.example.backend.dto.CardDtos.CreateCardRequest;
import com.example.backend.dto.CardDtos.DeleteCardResponse;
import com.example.backend.dto.CardDtos.LetterGenerateRequest;
import com.example.backend.dto.CardDtos.LetterGenerateResponse;
import com.example.backend.dto.CardDtos.MadeCard;
import com.example.backend.dto.CardDtos.MadeCardListResponse;
import com.example.backend.dto.CardDtos.ShareSceneResponse;
import com.example.backend.dto.CardDtos.TemplateCategory;
import com.example.backend.dto.CardDtos.TemplateListResponse;
import com.example.backend.dto.CapsuleDtos.CapsuleCreateRequest;
import com.example.backend.dto.CapsuleDtos.CapsuleListResponse;
import com.example.backend.dto.CapsuleDtos.CapsuleOpenResponse;
import com.example.backend.dto.CapsuleDtos.TimeCapsule;
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
import com.example.backend.dto.DayDtos.DayListResponse;
import com.example.backend.dto.DayDtos.DayRecord;
import com.example.backend.dto.DayDtos.DayReminder;
import com.example.backend.dto.DayDtos.DayReminderDueResponse;
import com.example.backend.dto.DayDtos.DayReminderRequest;
import com.example.backend.dto.DayDtos.DayUpsertRequest;
import com.example.backend.dto.DayDtos.DeleteDayResponse;
import com.example.backend.dto.ErrorDtos;
import com.example.backend.dto.FamilyDtos.CreateFamilyRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import com.example.backend.dto.VisitorDtos.HeartsRequest;
import com.example.backend.dto.VisitorDtos.UnlockedContentResponse;
import com.example.backend.dto.VisitorDtos.VisitorCardView;
import com.example.backend.dto.VisitorDtos.VisitorUnlockState;
import com.example.backend.service.AuthService;
import com.example.backend.service.BaguaService;
import com.example.backend.service.BirthdayService;
import com.example.backend.service.CardService;
import com.example.backend.service.CapsuleService;
import com.example.backend.service.DayService;
import com.example.backend.service.FamilyService;
import com.example.backend.service.FortuneService;
import com.example.backend.service.InteractService;
import com.example.backend.service.JieyouService;
import com.example.backend.service.NotifyService;
import com.example.backend.service.PendantService;
import com.example.backend.service.RankService;
import com.example.backend.service.VisitorService;
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

/** 契约路由(与 api/openapi.yaml 一一对应,46 个 operationId;/api/ping 为部署探活,不入契约) */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService authService;
    private final BaguaService baguaService;
    private final BirthdayService birthdayService;
    private final CardService cardService;
    private final CapsuleService capsuleService;
    private final DayService dayService;
    private final FamilyService familyService;
    private final FortuneService fortuneService;
    private final InteractService interactService;
    private final JieyouService jieyouService;
    private final NotifyService notifyService;
    private final PendantService pendantService;
    private final RankService rankService;
    private final VisitorService visitorService;

    public ApiController(AuthService authService,
                         BaguaService baguaService,
                         BirthdayService birthdayService,
                         CardService cardService,
                         CapsuleService capsuleService,
                         DayService dayService,
                         FamilyService familyService,
                         FortuneService fortuneService,
                         InteractService interactService,
                         JieyouService jieyouService,
                         NotifyService notifyService,
                         PendantService pendantService,
                         RankService rankService,
                         VisitorService visitorService) {
        this.authService = authService;
        this.baguaService = baguaService;
        this.birthdayService = birthdayService;
        this.cardService = cardService;
        this.capsuleService = capsuleService;
        this.dayService = dayService;
        this.familyService = familyService;
        this.fortuneService = fortuneService;
        this.interactService = interactService;
        this.jieyouService = jieyouService;
        this.notifyService = notifyService;
        this.pendantService = pendantService;
        this.rankService = rankService;
        this.visitorService = visitorService;
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

    // ---- 念家时·日子管家(契约 v1.6.0,tag=days) ----

    @GetMapping("/days")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayListResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayListResponse listDays() {
        return dayService.getDays();
    }

    @PostMapping("/days")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayRecord.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayRecord createDay(@RequestBody DayUpsertRequest request) {
        return dayService.create(request);
    }

    @GetMapping("/days/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayRecord.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayRecord getDay(@PathVariable Long id) {
        return dayService.getDay(id);
    }

    @PutMapping("/days/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayRecord.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayRecord updateDay(@PathVariable Long id, @RequestBody DayUpsertRequest request) {
        return dayService.update(id, request);
    }

    @DeleteMapping("/days/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DeleteDayResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DeleteDayResponse deleteDay(@PathVariable Long id) {
        return dayService.delete(id);
    }

    @PutMapping("/days/{id}/reminder")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayReminder.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayReminder updateDayReminder(@PathVariable Long id, @RequestBody DayReminderRequest request) {
        return dayService.updateReminder(id, request);
    }

    @GetMapping("/days/reminder-due")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DayReminderDueResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DayReminderDueResponse getReminderDue(@RequestParam(value = "date", required = false) String date) {
        return dayService.getReminderDue(date);
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

    // ---- 念家时·模板馆引擎(契约 v1.7.0,tag=cards/capsules/visitor) ----

    @GetMapping("/templates")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TemplateListResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public TemplateListResponse listTemplates(@RequestParam(value = "category", required = false) String category) {
        TemplateCategory parsed = null;
        if (category != null && !category.isBlank()) {
            try {
                parsed = TemplateCategory.valueOf(category);
            } catch (IllegalArgumentException e) {
                throw com.example.backend.common.ApiException.param("请您选择一个模板分类");
            }
        }
        return cardService.listTemplates(parsed);
    }

    @PostMapping("/templates/letter/generate")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LetterGenerateResponse.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public LetterGenerateResponse generateLetter(@RequestBody LetterGenerateRequest request) {
        return cardService.generateLetter(request);
    }

    @PostMapping("/cards")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MadeCard.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public MadeCard createCard(@RequestBody CreateCardRequest request) {
        return cardService.create(request);
    }

    @GetMapping("/cards")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MadeCardListResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public MadeCardListResponse listMyCards() {
        return cardService.listMyCards();
    }

    @GetMapping("/cards/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = MadeCard.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public MadeCard getMyCard(@PathVariable Long id) {
        return cardService.getMyCard(id);
    }

    @DeleteMapping("/cards/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DeleteCardResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public DeleteCardResponse deleteMyCard(@PathVariable Long id) {
        return cardService.delete(id);
    }

    @PostMapping("/cards/{id}/share")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ShareSceneResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public ShareSceneResponse issueShareScene(@PathVariable Long id) {
        return cardService.share(id);
    }

    @PostMapping("/capsules")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TimeCapsule.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public TimeCapsule sealCapsule(@RequestBody CapsuleCreateRequest request) {
        return capsuleService.seal(request);
    }

    @GetMapping("/capsules")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CapsuleListResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public CapsuleListResponse listMyCapsules() {
        return capsuleService.listMine();
    }

    @GetMapping("/capsules/{id}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TimeCapsule.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public TimeCapsule getMyCapsule(@PathVariable Long id) {
        return capsuleService.getMine(id);
    }

    @PostMapping("/capsules/{id}/open")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CapsuleOpenResponse.class))), @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public CapsuleOpenResponse openCapsule(@PathVariable Long id) {
        return capsuleService.open(id);
    }

    // ---- 免鉴权访客组(/api/visitor/**,AuthInterceptor 排除,永不 40100) ----

    @GetMapping("/visitor/cards/{scene}")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = VisitorCardView.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public VisitorCardView getVisitorCard(@PathVariable String scene,
                                          @RequestParam(value = "deviceId", required = false) String deviceId) {
        return visitorService.getCard(scene, deviceId);
    }

    @PostMapping("/visitor/cards/{scene}/hearts")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = VisitorUnlockState.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public VisitorUnlockState lightHeart(@PathVariable String scene, @RequestBody HeartsRequest request) {
        return visitorService.lightHeart(scene, request);
    }

    @GetMapping("/visitor/cards/{scene}/content")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UnlockedContentResponse.class))), @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class))), @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorDtos.ErrorResponse.class)))})
    public UnlockedContentResponse getUnlockedContent(@PathVariable String scene,
                                                      @RequestParam(value = "deviceId", required = false) String deviceId) {
        return visitorService.getContent(scene, deviceId);
    }
}
