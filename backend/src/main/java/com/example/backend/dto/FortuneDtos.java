package com.example.backend.dto;

import java.util.List;

/** 福签契约模型:PlateLevel / FortuneLevel / FortuneCard / FortuneTodayResponse / FortuneDrawResponse / BlessResult / CuifuResult */
public final class FortuneDtos {

    private FortuneDtos() {
    }

    /** 门牌等级:无/勤俭之家(500)/和睦之家(2000)/满门福气(5000)/福泽满堂(20000),只升不降 */
    public enum PlateLevel { NONE, BRONZE, SILVER, GOLD, JADE }

    /** 福签等级:平安(70%)/如意(25%)/鸿福(5%),纯稀有度玩法,不影响福值 */
    public enum FortuneLevel { PINGAN, RUYI, HONGFU }

    public static class FortuneCard {
        private Long familyId;
        private String date;
        private FortuneLevel level;
        private List<String> yiItems;
        private String blessText;

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
}
