package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 念家时·时光胶囊组 DTO(契约 v1.7.0 tag=capsules):封存/列表/详情/开箱。
 * TimeCapsule SEALED 态不下发内容字段——contentText/photoUrl/openedAt 序列化省略键(NON_NULL)。
 */
public final class CapsuleDtos {

    private CapsuleDtos() {
    }

    /** 胶囊类型:SELF 给未来的自己/PARTNER 给未来的 TA */
    public enum CapsuleType { SELF, PARTNER }

    /** 开箱规则预设(仅展示用,开箱判定唯一看 openDate) */
    public enum CapsuleOpenRule { ONE_YEAR_LATER, GAOKAO_SCORE_DAY, WEDDING_ANNIVERSARY, BABY_18 }

    /** 胶囊状态:SEALED 封存(内容门控)/OPENED 已开箱(内容对本人常开) */
    public enum CapsuleStatus { SEALED, OPENED }

    public static class CapsuleCreateRequest {
        private String type;
        private String openRule;
        private String openDate;
        private String coverHint;
        private String contentText;
        private String photoUrl;
        /** v1.8 新增:须为本人上传且 scene=CAPSULE 的未绑定照片(ORPHAN),传入则优先于 photoUrl 直传旧形态 */
        private String photoId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOpenRule() { return openRule; }
        public void setOpenRule(String openRule) { this.openRule = openRule; }
        public String getOpenDate() { return openDate; }
        public void setOpenDate(String openDate) { this.openDate = openDate; }
        public String getCoverHint() { return coverHint; }
        public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
    }

    /** SEALED 态省略 contentText/photoUrl/photoId/openedAt 键(契约:内容门控,不下发而非置空) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimeCapsule {
        private Long id;
        private CapsuleType type;
        private CapsuleOpenRule openRule;
        private String openDate;
        private String coverHint;
        private String contentText;
        private String photoUrl;
        private String photoId;
        private CapsuleStatus status;
        private String sealedAt;
        private String openedAt;
        private int daysUntilOpen;
        private boolean dueNow;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public CapsuleType getType() { return type; }
        public void setType(CapsuleType type) { this.type = type; }
        public CapsuleOpenRule getOpenRule() { return openRule; }
        public void setOpenRule(CapsuleOpenRule openRule) { this.openRule = openRule; }
        public String getOpenDate() { return openDate; }
        public void setOpenDate(String openDate) { this.openDate = openDate; }
        public String getCoverHint() { return coverHint; }
        public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
        public CapsuleStatus getStatus() { return status; }
        public void setStatus(CapsuleStatus status) { this.status = status; }
        public String getSealedAt() { return sealedAt; }
        public void setSealedAt(String sealedAt) { this.sealedAt = sealedAt; }
        public String getOpenedAt() { return openedAt; }
        public void setOpenedAt(String openedAt) { this.openedAt = openedAt; }
        public int getDaysUntilOpen() { return daysUntilOpen; }
        public void setDaysUntilOpen(int daysUntilOpen) { this.daysUntilOpen = daysUntilOpen; }
        public boolean isDueNow() { return dueNow; }
        public void setDueNow(boolean dueNow) { this.dueNow = dueNow; }
    }

    public static class CapsuleListResponse {
        private java.util.List<TimeCapsule> items;
        private int dueCount;

        public java.util.List<TimeCapsule> getItems() { return items; }
        public void setItems(java.util.List<TimeCapsule> items) { this.items = items; }
        public int getDueCount() { return dueCount; }
        public void setDueCount(int dueCount) { this.dueCount = dueCount; }
    }

    /** 开箱响应恒 OPENED,内容字段必发(契约 required) */
    public static class CapsuleOpenResponse {
        private Long id;
        private CapsuleType type;
        private CapsuleOpenRule openRule;
        private String openDate;
        private String coverHint;
        private String contentText;
        private String photoUrl;
        private String photoId;
        private String status;
        private String sealedAt;
        private String openedAt;
        private int daysUntilOpen;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public CapsuleType getType() { return type; }
        public void setType(CapsuleType type) { this.type = type; }
        public CapsuleOpenRule getOpenRule() { return openRule; }
        public void setOpenRule(CapsuleOpenRule openRule) { this.openRule = openRule; }
        public String getOpenDate() { return openDate; }
        public void setOpenDate(String openDate) { this.openDate = openDate; }
        public String getCoverHint() { return coverHint; }
        public void setCoverHint(String coverHint) { this.coverHint = coverHint; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSealedAt() { return sealedAt; }
        public void setSealedAt(String sealedAt) { this.sealedAt = sealedAt; }
        public String getOpenedAt() { return openedAt; }
        public void setOpenedAt(String openedAt) { this.openedAt = openedAt; }
        public int getDaysUntilOpen() { return daysUntilOpen; }
        public void setDaysUntilOpen(int daysUntilOpen) { this.daysUntilOpen = daysUntilOpen; }
    }
}
