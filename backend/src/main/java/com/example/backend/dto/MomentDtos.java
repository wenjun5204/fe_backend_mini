package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 念家时·家人瞬间组 DTO(契约 v1.8.0 tag=moments):照片+一句话家族时间线、真好、收藏进日子、素材池。
 * 时间线发布人昵称不下发(前端经 /api/family 成员列表解析 userId→昵称);
 * 素材池端点服务端解析昵称(契约各异,按契约实现)。
 */
public final class MomentDtos {

    private MomentDtos() {
    }

    public static class MomentCreateRequest {
        private String photoId;
        private String text;

        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class FamilyMomentView {
        private Long id;
        private Long userId;
        private String photoId;
        private String photoUrl;
        private String text;
        private String createdAt;
        private int likeCount;
        private boolean likedByMe;
        private int collectedCount;
        private List<Long> collectedByMeDayIds;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
        public boolean isLikedByMe() { return likedByMe; }
        public void setLikedByMe(boolean likedByMe) { this.likedByMe = likedByMe; }
        public int getCollectedCount() { return collectedCount; }
        public void setCollectedCount(int collectedCount) { this.collectedCount = collectedCount; }
        public List<Long> getCollectedByMeDayIds() { return collectedByMeDayIds; }
        public void setCollectedByMeDayIds(List<Long> collectedByMeDayIds) { this.collectedByMeDayIds = collectedByMeDayIds; }
    }

    public static class MomentListResponse {
        private List<FamilyMomentView> items;
        /** 还有更早记录时=下页游标(下页 beforeId),否则缺省(契约 nullable) */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long nextCursor;

        public List<FamilyMomentView> getItems() { return items; }
        public void setItems(List<FamilyMomentView> items) { this.items = items; }
        public Long getNextCursor() { return nextCursor; }
        public void setNextCursor(Long nextCursor) { this.nextCursor = nextCursor; }
    }

    public static class MomentLikeResponse {
        private int likeCount;
        private boolean likedByMe;

        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
        public boolean isLikedByMe() { return likedByMe; }
        public void setLikedByMe(boolean likedByMe) { this.likedByMe = likedByMe; }
    }

    public static class MomentCollectRequest {
        private Long dayId;

        public Long getDayId() { return dayId; }
        public void setDayId(Long dayId) { this.dayId = dayId; }
    }

    public static class MomentCollectResponse {
        private int collectedCount;
        private boolean collectedByMe;
        private Long dayId;

        public int getCollectedCount() { return collectedCount; }
        public void setCollectedCount(int collectedCount) { this.collectedCount = collectedCount; }
        public boolean isCollectedByMe() { return collectedByMe; }
        public void setCollectedByMe(boolean collectedByMe) { this.collectedByMe = collectedByMe; }
        public Long getDayId() { return dayId; }
        public void setDayId(Long dayId) { this.dayId = dayId; }
    }

    public static class DeleteMomentResponse {
        private boolean deleted;

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }

    public static class DayCollectedMomentItem {
        private Long momentId;
        private String photoUrl;
        private String text;
        private String authorName;
        private String collectedBy;
        private String collectedAt;

        public Long getMomentId() { return momentId; }
        public void setMomentId(Long momentId) { this.momentId = momentId; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getCollectedBy() { return collectedBy; }
        public void setCollectedBy(String collectedBy) { this.collectedBy = collectedBy; }
        public String getCollectedAt() { return collectedAt; }
        public void setCollectedAt(String collectedAt) { this.collectedAt = collectedAt; }
    }

    public static class DayCollectedMomentsResponse {
        private List<DayCollectedMomentItem> items;
        private int total;

        public List<DayCollectedMomentItem> getItems() { return items; }
        public void setItems(List<DayCollectedMomentItem> items) { this.items = items; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }
}
