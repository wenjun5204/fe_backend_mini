package com.example.backend.dto;

/** 订阅消息契约模型:NotifySubscribeRequest / NotifySubscribeResponse / NotifyStatusResponse */
public final class NotifyDtos {

    private NotifyDtos() {
    }

    public static class NotifySubscribeRequest {
        /** 订阅消息模板 ID(微信后台申请;开发期占位联调) */
        private String templateId;
        /** 本次授权是否接受(拒绝也登记,用于频率透明展示) */
        private boolean accepted;

        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public boolean isAccepted() { return accepted; }
        public void setAccepted(boolean accepted) { this.accepted = accepted; }
    }

    public static class NotifySubscribeResponse {
        private boolean registered;
        /** 今日剩余可发送条数(频控≤4 条/人/天) */
        private int remainingQuotaToday;

        public boolean isRegistered() { return registered; }
        public void setRegistered(boolean registered) { this.registered = registered; }
        public int getRemainingQuotaToday() { return remainingQuotaToday; }
        public void setRemainingQuotaToday(int remainingQuotaToday) {
            this.remainingQuotaToday = remainingQuotaToday;
        }
    }

    public static class NotifyStatusResponse {
        /** 是否存在有效授权额度 */
        private boolean subscribed;
        private int remainingQuotaToday;
        /** 每日上限(4);设置页展示「您每天最多收到 4 条提醒,不会多打扰」 */
        private int dailyLimit;

        public boolean isSubscribed() { return subscribed; }
        public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }
        public int getRemainingQuotaToday() { return remainingQuotaToday; }
        public void setRemainingQuotaToday(int remainingQuotaToday) {
            this.remainingQuotaToday = remainingQuotaToday;
        }
        public int getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    }
}
