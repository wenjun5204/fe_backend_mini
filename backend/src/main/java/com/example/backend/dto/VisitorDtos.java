package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 念家时·免鉴权访客组 DTO(契约 v1.7.0 tag=visitor):落地/点亮心/解锁取内容。
 * publicPayload 与 hiddenContent 按 kind 异构——未适用字段序列化省略键(NON_NULL)。
 */
public final class VisitorDtos {

    private VisitorDtos() {
    }

    public static class HeartsRequest {
        private String deviceId;

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    }

    public static class VisitorCardView {
        private CardDtos.CardKind templateKind;
        private VisitorPublicPayload publicPayload;
        private String lockedTeaser;
        private int hearts;
        private boolean unlocked;

        public CardDtos.CardKind getTemplateKind() { return templateKind; }
        public void setTemplateKind(CardDtos.CardKind templateKind) { this.templateKind = templateKind; }
        public VisitorPublicPayload getPublicPayload() { return publicPayload; }
        public void setPublicPayload(VisitorPublicPayload publicPayload) { this.publicPayload = publicPayload; }
        public String getLockedTeaser() { return lockedTeaser; }
        public void setLockedTeaser(String lockedTeaser) { this.lockedTeaser = lockedTeaser; }
        public int getHearts() { return hearts; }
        public void setHearts(int hearts) { this.hearts = hearts; }
        public boolean isUnlocked() { return unlocked; }
        public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    }

    /** 体面内容(按卡片可见策略脱敏,不含 userId/昵称等任何 PII) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VisitorPublicPayload {
        private String recipient;
        private List<String> paragraphs;
        private String signText;
        private String relation;
        private String startDate;
        private Integer daysTogether;
        private String styleKey;
        private CapsuleDtos.CapsuleType type;
        private String coverHint;
        private String openDate;
        private Integer daysUntilOpen;
        /** 仅 BIRTHDAY_CARD:封面照片 capability 路径(≤3,素材瞬间已删则跳过) */
        private List<String> photoUrls;
        /** 仅 BIRTHDAY_CARD:眉题,恒「生日快乐」 */
        private String kicker;

        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        public List<String> getParagraphs() { return paragraphs; }
        public void setParagraphs(List<String> paragraphs) { this.paragraphs = paragraphs; }
        public String getSignText() { return signText; }
        public void setSignText(String signText) { this.signText = signText; }
        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public Integer getDaysTogether() { return daysTogether; }
        public void setDaysTogether(Integer daysTogether) { this.daysTogether = daysTogether; }
        public String getStyleKey() { return styleKey; }
        public void setStyleKey(String styleKey) { this.styleKey = styleKey; }
        public CapsuleDtos.CapsuleType getType() { return type; }
        public void setType(CapsuleDtos.CapsuleType type) { this.type = type; }
        public String getCoverHint() { return coverHint; }
        public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
        public String getOpenDate() { return openDate; }
        public void setOpenDate(String openDate) { this.openDate = openDate; }
        public Integer getDaysUntilOpen() { return daysUntilOpen; }
        public void setDaysUntilOpen(Integer daysUntilOpen) { this.daysUntilOpen = daysUntilOpen; }
        public List<String> getPhotoUrls() { return photoUrls; }
        public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }
        public String getKicker() { return kicker; }
        public void setKicker(String kicker) { this.kicker = kicker; }
    }

    public static class VisitorUnlockState {
        private int hearts;
        private boolean unlocked;
        private String remainingHint;
        private int totalHearts;

        public int getHearts() { return hearts; }
        public void setHearts(int hearts) { this.hearts = hearts; }
        public boolean isUnlocked() { return unlocked; }
        public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
        public String getRemainingHint() { return remainingHint; }
        public void setRemainingHint(String remainingHint) { this.remainingHint = remainingHint; }
        public int getTotalHearts() { return totalHearts; }
        public void setTotalHearts(int totalHearts) { this.totalHearts = totalHearts; }
    }

    public static class UnlockedContentResponse {
        private CardDtos.CardKind templateKind;
        private HiddenContent hiddenContent;
        private CtaCopy ctaCopy;

        public CardDtos.CardKind getTemplateKind() { return templateKind; }
        public void setTemplateKind(CardDtos.CardKind templateKind) { this.templateKind = templateKind; }
        public HiddenContent getHiddenContent() { return hiddenContent; }
        public void setHiddenContent(HiddenContent hiddenContent) { this.hiddenContent = hiddenContent; }
        public CtaCopy getCtaCopy() { return ctaCopy; }
        public void setCtaCopy(CtaCopy ctaCopy) { this.ctaCopy = ctaCopy; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HiddenContent {
        private List<String> paragraphs;
        private String signText;
        private String hiddenLine;
        private String contentText;
        private String photoUrl;
        /** 仅 BIRTHDAY_CARD:祝福语本体(体面内容=关系+照片,点亮 3 心解锁) */
        private String blessingText;

        public List<String> getParagraphs() { return paragraphs; }
        public void setParagraphs(List<String> paragraphs) { this.paragraphs = paragraphs; }
        public String getSignText() { return signText; }
        public void setSignText(String signText) { this.signText = signText; }
        public String getHiddenLine() { return hiddenLine; }
        public void setHiddenLine(String hiddenLine) { this.hiddenLine = hiddenLine; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getBlessingText() { return blessingText; }
        public void setBlessingText(String blessingText) { this.blessingText = blessingText; }
    }

    public static class CtaCopy {
        private String title;
        private String sub;
        private String buttonText;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSub() { return sub; }
        public void setSub(String sub) { this.sub = sub; }
        public String getButtonText() { return buttonText; }
        public void setButtonText(String buttonText) { this.buttonText = buttonText; }
    }
}
