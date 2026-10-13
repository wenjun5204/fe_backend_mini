package com.example.backend.dto;

import java.util.List;

/** 福签契约模型:PlateLevel / FortuneLevel / Gua / FortuneCard / FortuneTodayResponse / FortuneDrawResponse / BlessResult / CuifuResult / FortuneTaskDoneRequest */
public final class FortuneDtos {

    private FortuneDtos() {
    }

    /** 门牌等级:无/勤俭之家(500)/和睦之家(2000)/满门福气(5000)/福泽满堂(20000),只升不降 */
    public enum PlateLevel { NONE, BRONZE, SILVER, GOLD, JADE }

    /** 福签等级:平安(70%)/如意(25%)/鸿福(5%),纯稀有度玩法,不影响福值 */
    public enum FortuneLevel { PINGAN, RUYI, HONGFU }

    /** 八卦卦位;家族+日期唯一确定,全家同卦,每日变化;只影响卡面主题 */
    public enum Gua { QIAN, DUI, LI, ZHEN, XUN, KAN, GEN, KUN }

    public static class FortuneCard {
        private Long familyId;
        private String date;
        private FortuneLevel level;
        private List<String> yiItems;
        private String blessText;
        /** 节日限定标记(如「新春」「中秋」),非节日日为 null */
        private String festival;
        /** 卦位(确定性映射,全家同卦,每日轮换;只影响卡面主题) */
        private Gua gua;
        /** 卦位对应卡面主题(乾=云鹤/兑=锦鲤/离=灯笼/震=春雷/巽=风铃/坎=锦鲤戏水/艮=山景/坤=花开) */
        private String cardTheme;

        public Long getFamilyId() { return familyId; }
        public void setFamilyId(Long familyId) { this.familyId = familyId; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public FortuneLevel getLevel() { return level; }
        public void setLevel(FortuneLevel level) { this.level = level; }
        public List<String> getYiItems() { return yiItems; }
        public void setYiItems(List<String> yiItems) { this.yiItems = yiItems; }
        public String getBlessText() { return blessText; }
        public void setBlessText(String blessText) { this.blessText = blessText; }
        public String getFestival() { return festival; }
        public void setFestival(String festival) { this.festival = festival; }
        public Gua getGua() { return gua; }
        public void setGua(Gua gua) { this.gua = gua; }
        public String getCardTheme() { return cardTheme; }
        public void setCardTheme(String cardTheme) { this.cardTheme = cardTheme; }
    }

    public static class FortuneTodayResponse {
        private FortuneCard card;
        private boolean drawn;
        private boolean shared;
        private boolean taskDone;
        private long personalBless;

        public FortuneCard getCard() { return card; }
        public void setCard(FortuneCard card) { this.card = card; }
        public boolean isDrawn() { return drawn; }
        public void setDrawn(boolean drawn) { this.drawn = drawn; }
        public boolean isShared() { return shared; }
        public void setShared(boolean shared) { this.shared = shared; }
        public boolean isTaskDone() { return taskDone; }
        public void setTaskDone(boolean taskDone) { this.taskDone = taskDone; }
        public long getPersonalBless() { return personalBless; }
        public void setPersonalBless(long personalBless) { this.personalBless = personalBless; }
    }

    public static class FortuneDrawResponse {
        private FortuneCard card;
        private long personalBless;
        private long familyTotalBless;
        private String praiseText;

        public FortuneCard getCard() { return card; }
        public void setCard(FortuneCard card) { this.card = card; }
        public long getPersonalBless() { return personalBless; }
        public void setPersonalBless(long personalBless) { this.personalBless = personalBless; }
        public long getFamilyTotalBless() { return familyTotalBless; }
        public void setFamilyTotalBless(long familyTotalBless) { this.familyTotalBless = familyTotalBless; }
        public String getPraiseText() { return praiseText; }
        public void setPraiseText(String praiseText) { this.praiseText = praiseText; }
    }

    public static class BlessResult {
        private int amount;
        private long personalBless;
        private long familyTotalBless;
        private PlateLevel plateLevel;

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public long getPersonalBless() { return personalBless; }
        public void setPersonalBless(long personalBless) { this.personalBless = personalBless; }
        public long getFamilyTotalBless() { return familyTotalBless; }
        public void setFamilyTotalBless(long familyTotalBless) { this.familyTotalBless = familyTotalBless; }
        public PlateLevel getPlateLevel() { return plateLevel; }
        public void setPlateLevel(PlateLevel plateLevel) { this.plateLevel = plateLevel; }
    }

    public static class CuifuResult {
        private boolean sent;
        private int remainCount;

        public boolean isSent() { return sent; }
        public void setSent(boolean sent) { this.sent = sent; }
        public int getRemainCount() { return remainCount; }
        public void setRemainCount(int remainCount) { this.remainCount = remainCount; }
    }

    /** 完成今日宜事项请求:taskItem 可选(须属于当日卡面 yiItems;缺省兼容旧行为) */
    public static class FortuneTaskDoneRequest {
        /** 完成的今日宜事项文本(用于八卦阵早睡/喝水/打电话类进度统计) */
        private String taskItem;

        public String getTaskItem() { return taskItem; }
        public void setTaskItem(String taskItem) { this.taskItem = taskItem; }
    }
}
