package com.example.backend.dto;

import java.util.List;

/** 排行榜契约模型:FamilyRankItem / FamilyRankResponse / FriendRankItem / FriendRankResponse */
public final class RankDtos {

    private RankDtos() {
    }

    public static class FamilyRankItem {
        private int rank;
        private Long userId;
        private String nickname;
        private int bless;
        private int streakDays;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public int getBless() { return bless; }
        public void setBless(int bless) { this.bless = bless; }
        public int getStreakDays() { return streakDays; }
        public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
    }

    public static class FamilyRankResponse {
        private String weekStart;
        private List<FamilyRankItem> items;

        public String getWeekStart() { return weekStart; }
        public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
        public List<FamilyRankItem> getItems() { return items; }
        public void setItems(List<FamilyRankItem> items) { this.items = items; }
    }

    public static class FriendRankItem {
        private int rank;
        private Long familyId;
        private String familyName;
        private long bless;
        private FortuneDtos.PlateLevel plateLevel;
        private boolean isMine;

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public Long getFamilyId() { return familyId; }
        public void setFamilyId(Long familyId) { this.familyId = familyId; }
        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        public long getBless() { return bless; }
        public void setBless(long bless) { this.bless = bless; }
        public FortuneDtos.PlateLevel getPlateLevel() { return plateLevel; }
        public void setPlateLevel(FortuneDtos.PlateLevel plateLevel) { this.plateLevel = plateLevel; }
        public boolean getIsMine() { return isMine; }
        public void setIsMine(boolean mine) { isMine = mine; }
    }

    public static class FriendRankResponse {
        private List<FriendRankItem> items;
        private String chaseText;

        public List<FriendRankItem> getItems() { return items; }
        public void setItems(List<FriendRankItem> items) { this.items = items; }
        public String getChaseText() { return chaseText; }
        public void setChaseText(String chaseText) { this.chaseText = chaseText; }
    }
}
