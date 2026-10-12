package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 念家时·模板馆卡片组 DTO(契约 v1.7.0 tag=cards):模板清单/感恩信生成/三类卡片定稿存档。
 * 枚举入参一律以 String 接收、服务端手工解析(40000 语义自控,不依赖 Jackson 绑定错误);
 * nullable 字段(NON_NULL 标注类)序列化时省略键,对齐契约「未适用的字段不出现」。
 */
public final class CardDtos {

    private CardDtos() {
    }

    // ---- 枚举(出参序列化用;入参见各 Request 的 String 字段) ----

    /** 模板键(服务端配置主键),与 TemplateCatalog 静态配置一一对应 */
    public enum TemplateKey { LETTER, DAYS_CARD, CAPSULE, BIRTHDAY_CARD, HARD_FOR_DAD, WEDDING_CARD }

    /** 模板分类(前端分类 Tab 过滤) */
    public enum TemplateCategory { FESTIVAL, FAMILY, COUPLE, FUN }

    /** 感恩信 5 个固定素材键(与 LetterTextLib.STORY_PARAS 键一致,值域=LetterGenerateRequest.stories) */
    public enum StoryKey {
        她总给我留饭, 送我上学那些年, 她爱唠叨但都是好意, 她一个人把我带大, 我离家后她学会视频
    }

    /** 信件语气:TEAR 戳泪点/FUN 幽默/CALM 平淡 */
    public enum LetterTone { TEAR, FUN, CALM }

    /** 生成来源:AI 优先;无 Key/异常静默降级本地文案库(FALLBACK),仅埋点用 */
    public enum LetterSource { AI, FALLBACK }

    /** 段落角色:OPENING 开头/STORY 故事段/CLOSING 收尾段 */
    public enum LetterSegmentRole { OPENING, STORY, CLOSING }

    /** 卡片类型(可制作的四类):BIRTHDAY_CARD 生日福卡(v1.8 新增,随时可生成,收藏瞬间照片做封面) */
    public enum CardKind { LETTER, DAYS_CARD, CAPSULE, BIRTHDAY_CARD }

    /** 卡片状态:ACTIVE 正常/DELETED 软删 */
    public enum CardStatus { ACTIVE, DELETED }

    // ---- 模板馆 ----

    public static class TemplatePreviewMeta {
        private String kicker;
        private String emoji;
        private String sampleText;
        private String backgroundKey;
        private String sub;
        private List<String> feats;
        private String usedLabel;
        private String durationLabel;
        private String freeLabel;
        private String hint;

        public String getKicker() { return kicker; }
        public void setKicker(String kicker) { this.kicker = kicker; }
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        public String getSampleText() { return sampleText; }
        public void setSampleText(String sampleText) { this.sampleText = sampleText; }
        public String getBackgroundKey() { return backgroundKey; }
        public void setBackgroundKey(String backgroundKey) { this.backgroundKey = backgroundKey; }
        public String getSub() { return sub; }
        public void setSub(String sub) { this.sub = sub; }
        public List<String> getFeats() { return feats; }
        public void setFeats(List<String> feats) { this.feats = feats; }
        public String getUsedLabel() { return usedLabel; }
        public void setUsedLabel(String usedLabel) { this.usedLabel = usedLabel; }
        public String getDurationLabel() { return durationLabel; }
        public void setDurationLabel(String durationLabel) { this.durationLabel = durationLabel; }
        public String getFreeLabel() { return freeLabel; }
        public void setFreeLabel(String freeLabel) { this.freeLabel = freeLabel; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateInfo {
        private TemplateKey key;
        private String name;
        private TemplateCategory category;
        private boolean live;
        private int sort;
        private String festivalDate;
        private String festivalLabel;
        private TemplatePreviewMeta previewMeta;
        private String makerPage;

        public TemplateKey getKey() { return key; }
        public void setKey(TemplateKey key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public TemplateCategory getCategory() { return category; }
        public void setCategory(TemplateCategory category) { this.category = category; }
        public boolean isLive() { return live; }
        public void setLive(boolean live) { this.live = live; }
        public int getSort() { return sort; }
        public void setSort(int sort) { this.sort = sort; }
        public String getFestivalDate() { return festivalDate; }
        public void setFestivalDate(String festivalDate) { this.festivalDate = festivalDate; }
        public String getFestivalLabel() { return festivalLabel; }
        public void setFestivalLabel(String festivalLabel) { this.festivalLabel = festivalLabel; }
        public TemplatePreviewMeta getPreviewMeta() { return previewMeta; }
        public void setPreviewMeta(TemplatePreviewMeta previewMeta) { this.previewMeta = previewMeta; }
        public String getMakerPage() { return makerPage; }
        public void setMakerPage(String makerPage) { this.makerPage = makerPage; }
    }

    public static class TemplateListResponse {
        private List<TemplateInfo> items;

        public List<TemplateInfo> getItems() { return items; }
        public void setItems(List<TemplateInfo> items) { this.items = items; }
    }

    // ---- 感恩信生成 ----

    public static class LetterGenerateRequest {
        private String recipient;
        @ArraySchema(schema = @Schema(implementation = StoryKey.class),
                arraySchema = @Schema(description = "故事素材多选,1-5 项;非法值由服务端校验回 40000"))
        private List<String> stories;
        @Schema(implementation = LetterTone.class,
                description = "语气;非法值由服务端校验回 40000")
        private String tone;

        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        public List<String> getStories() { return stories; }
        public void setStories(List<String> stories) { this.stories = stories; }
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
    }

    public static class LetterSegment {
        private int index;
        private LetterSegmentRole role;
        @Schema(implementation = StoryKey.class, nullable = true,
                description = "仅 role=STORY 段有值;OPENING/CLOSING 为 null")
        private String storyKey;
        private List<String> variants;
        private String selected;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public LetterSegmentRole getRole() { return role; }
        public void setRole(LetterSegmentRole role) { this.role = role; }
        public String getStoryKey() { return storyKey; }
        public void setStoryKey(String storyKey) { this.storyKey = storyKey; }
        public List<String> getVariants() { return variants; }
        public void setVariants(List<String> variants) { this.variants = variants; }
        public String getSelected() { return selected; }
        public void setSelected(String selected) { this.selected = selected; }
    }

    public static class LetterGenerateResponse {
        private String generationId;
        private LetterSource source;
        private List<LetterSegment> paragraphs;
        private String signText;

        public String getGenerationId() { return generationId; }
        public void setGenerationId(String generationId) { this.generationId = generationId; }
        public LetterSource getSource() { return source; }
        public void setSource(LetterSource source) { this.source = source; }
        public List<LetterSegment> getParagraphs() { return paragraphs; }
        public void setParagraphs(List<LetterSegment> paragraphs) { this.paragraphs = paragraphs; }
        public String getSignText() { return signText; }
        public void setSignText(String signText) { this.signText = signText; }
    }

    // ---- 卡片定稿存档 ----

    /** 定稿内容,按 kind 异构;入参枚举字段以 String 接收,序列化时未适用字段省略键 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CardPayload {
        private String recipient;
        private String tone;
        private List<String> paragraphs;
        private String signText;
        private String relation;
        private String startDate;
        private String styleKey;
        private Long capsuleId;
        private String coverHint;
        private String openDate;
        private String type;
        /** 仅 BIRTHDAY_CARD:关联 DayRecord id(须 kind=BIRTHDAY 且本人可见) */
        private Long dayId;
        /** 仅 BIRTHDAY_CARD:祝福语 1-40 字(过违禁词表) */
        private String blessingText;
        /** 仅 BIRTHDAY_CARD:选中的瞬间 id 1-3 项(须在该 dayId 收藏素材池中) */
        private List<Long> momentIds;

        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
        public List<String> getParagraphs() { return paragraphs; }
        public void setParagraphs(List<String> paragraphs) { this.paragraphs = paragraphs; }
        public String getSignText() { return signText; }
        public void setSignText(String signText) { this.signText = signText; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getStyleKey() { return styleKey; }
        public void setStyleKey(String styleKey) { this.styleKey = styleKey; }
        public Long getCapsuleId() { return capsuleId; }
        public void setCapsuleId(Long capsuleId) { this.capsuleId = capsuleId; }
        public String getCoverHint() { return coverHint; }
        public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
        public String getOpenDate() { return openDate; }
        public void setOpenDate(String openDate) { this.openDate = openDate; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getDayId() { return dayId; }
        public void setDayId(Long dayId) { this.dayId = dayId; }
        public String getBlessingText() { return blessingText; }
        public void setBlessingText(String blessingText) { this.blessingText = blessingText; }
        public List<Long> getMomentIds() { return momentIds; }
        public void setMomentIds(List<Long> momentIds) { this.momentIds = momentIds; }
    }

    public static class CreateCardRequest {
        private String templateKind;
        private CardPayload payload;
        private Long sourceDayId;
        private Long capsuleId;

        public String getTemplateKind() { return templateKind; }
        public void setTemplateKind(String templateKind) { this.templateKind = templateKind; }
        public CardPayload getPayload() { return payload; }
        public void setPayload(CardPayload payload) { this.payload = payload; }
        public Long getSourceDayId() { return sourceDayId; }
        public void setSourceDayId(Long sourceDayId) { this.sourceDayId = sourceDayId; }
        public Long getCapsuleId() { return capsuleId; }
        public void setCapsuleId(Long capsuleId) { this.capsuleId = capsuleId; }
    }

    public static class MadeCard {
        private Long id;
        private CardKind templateKind;
        private CardPayload payload;
        private Long sourceDayId;
        private Long capsuleId;
        private String shareScene;
        private CardStatus status;
        private String createdAt;
        private int totalHearts;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public CardKind getTemplateKind() { return templateKind; }
        public void setTemplateKind(CardKind templateKind) { this.templateKind = templateKind; }
        public CardPayload getPayload() { return payload; }
        public void setPayload(CardPayload payload) { this.payload = payload; }
        public Long getSourceDayId() { return sourceDayId; }
        public void setSourceDayId(Long sourceDayId) { this.sourceDayId = sourceDayId; }
        public Long getCapsuleId() { return capsuleId; }
        public void setCapsuleId(Long capsuleId) { this.capsuleId = capsuleId; }
        public String getShareScene() { return shareScene; }
        public void setShareScene(String shareScene) { this.shareScene = shareScene; }
        public CardStatus getStatus() { return status; }
        public void setStatus(CardStatus status) { this.status = status; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public int getTotalHearts() { return totalHearts; }
        public void setTotalHearts(int totalHearts) { this.totalHearts = totalHearts; }
    }

    public static class MadeCardListResponse {
        private List<MadeCard> items;
        private int total;
        private int heartsReceived;

        public List<MadeCard> getItems() { return items; }
        public void setItems(List<MadeCard> items) { this.items = items; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getHeartsReceived() { return heartsReceived; }
        public void setHeartsReceived(int heartsReceived) { this.heartsReceived = heartsReceived; }
    }

    public static class DeleteCardResponse {
        private boolean deleted;

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }

    public static class ShareSceneResponse {
        private String scene;
        private String pagePath;

        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
        public String getPagePath() { return pagePath; }
        public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    }
}
