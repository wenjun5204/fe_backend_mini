package com.example.backend.dto;

import com.example.backend.dto.CardDtos.TemplateKey;
import java.util.List;

/**
 * 节日/上线预约契约模型(v1.8.0 tag=festival)。
 * templateKey 在请求中为 String 承接,由 service 层校验枚举值域(POST 越值域 40400/DELETE 非法 40000,契约各异)。
 */
public final class FestivalDtos {

    private FestivalDtos() {
    }

    public static class FestivalReservationRequest {
        private String templateKey;

        public String getTemplateKey() { return templateKey; }
        public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    }

    public static class FestivalReservation {
        private TemplateKey templateKey;
        private boolean reserved;
        private String reservedAt;

        public TemplateKey getTemplateKey() { return templateKey; }
        public void setTemplateKey(TemplateKey templateKey) { this.templateKey = templateKey; }
        public boolean isReserved() { return reserved; }
        public void setReserved(boolean reserved) { this.reserved = reserved; }
        public String getReservedAt() { return reservedAt; }
        public void setReservedAt(String reservedAt) { this.reservedAt = reservedAt; }
    }

    public static class ReservationListItem {
        private TemplateKey templateKey;
        private String name;
        private boolean live;
        private String festivalLabel;
        private String festivalDate;
        private Integer daysUntil;
        private String reservedAt;

        public TemplateKey getTemplateKey() { return templateKey; }
        public void setTemplateKey(TemplateKey templateKey) { this.templateKey = templateKey; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isLive() { return live; }
        public void setLive(boolean live) { this.live = live; }
        public String getFestivalLabel() { return festivalLabel; }
        public void setFestivalLabel(String festivalLabel) { this.festivalLabel = festivalLabel; }
        public String getFestivalDate() { return festivalDate; }
        public void setFestivalDate(String festivalDate) { this.festivalDate = festivalDate; }
        public Integer getDaysUntil() { return daysUntil; }
        public void setDaysUntil(Integer daysUntil) { this.daysUntil = daysUntil; }
        public String getReservedAt() { return reservedAt; }
        public void setReservedAt(String reservedAt) { this.reservedAt = reservedAt; }
    }

    public static class ReservationListResponse {
        private List<ReservationListItem> items;
        private int total;

        public List<ReservationListItem> getItems() { return items; }
        public void setItems(List<ReservationListItem> items) { this.items = items; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }

    public static class CancelReservationResponse {
        private boolean deleted;

        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }

    public static class FestivalDueItem {
        private TemplateKey templateKey;
        private String festivalLabel;
        private String emoji;
        private String festivalDate;
        private int daysUntil;
        private boolean live;
        private boolean reservedByMe;
        private boolean dueSoon;

        public TemplateKey getTemplateKey() { return templateKey; }
        public void setTemplateKey(TemplateKey templateKey) { this.templateKey = templateKey; }
        public String getFestivalLabel() { return festivalLabel; }
        public void setFestivalLabel(String festivalLabel) { this.festivalLabel = festivalLabel; }
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        public String getFestivalDate() { return festivalDate; }
        public void setFestivalDate(String festivalDate) { this.festivalDate = festivalDate; }
        public int getDaysUntil() { return daysUntil; }
        public void setDaysUntil(int daysUntil) { this.daysUntil = daysUntil; }
        public boolean isLive() { return live; }
        public void setLive(boolean live) { this.live = live; }
        public boolean isReservedByMe() { return reservedByMe; }
        public void setReservedByMe(boolean reservedByMe) { this.reservedByMe = reservedByMe; }
        public boolean isDueSoon() { return dueSoon; }
        public void setDueSoon(boolean dueSoon) { this.dueSoon = dueSoon; }
    }

    public static class FestivalDueResponse {
        private List<FestivalDueItem> items;
        private int total;

        public List<FestivalDueItem> getItems() { return items; }
        public void setItems(List<FestivalDueItem> items) { this.items = items; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }
}
