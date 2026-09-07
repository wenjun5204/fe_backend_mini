package com.example.backend.dto;

import java.util.List;

/** 家族契约模型:CreateFamilyRequest / JoinFamilyRequest / TargetUserRequest / MemberStatus / FamilyDetail / InviteInfo */
public final class FamilyDtos {

    private FamilyDtos() {
    }

    public static class CreateFamilyRequest {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class JoinFamilyRequest {
        private String inviteCode;

        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    }

    public static class TargetUserRequest {
        private Long targetUserId;

        public Long getTargetUserId() { return targetUserId; }
        public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    }

    public static class MemberStatus {
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private String remark;
        private String role;
        private long personalBless;
        private int streakDays;
        private boolean drawnToday;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public long getPersonalBless() { return personalBless; }
        public void setPersonalBless(long personalBless) { this.personalBless = personalBless; }
        public int getStreakDays() { return streakDays; }
        public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
        public boolean isDrawnToday() { return drawnToday; }
        public void setDrawnToday(boolean drawnToday) { this.drawnToday = drawnToday; }
    }

    public static class FamilyDetail {
        private boolean notJoined;
        private Long familyId;
        private String familyName;
        private String inviteCode;
        private long totalBless;
        private FortuneDtos.PlateLevel plateLevel;
        private long nextPlateBless;
        private List<MemberStatus> members;

        public boolean isNotJoined() { return notJoined; }
        public void setNotJoined(boolean notJoined) { this.notJoined = notJoined; }
        public Long getFamilyId() { return familyId; }
        public void setFamilyId(Long familyId) { this.familyId = familyId; }
        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
        public long getTotalBless() { return totalBless; }
        public void setTotalBless(long totalBless) { this.totalBless = totalBless; }
        public FortuneDtos.PlateLevel getPlateLevel() { return plateLevel; }
        public void setPlateLevel(FortuneDtos.PlateLevel plateLevel) { this.plateLevel = plateLevel; }
        public long getNextPlateBless() { return nextPlateBless; }
        public void setNextPlateBless(long nextPlateBless) { this.nextPlateBless = nextPlateBless; }
        public List<MemberStatus> getMembers() { return members; }
        public void setMembers(List<MemberStatus> members) { this.members = members; }
    }

    public static class InviteInfo {
        private String familyName;
        private Long familyId;
        private int memberCount;
        private String inviteText;

        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        public Long getFamilyId() { return familyId; }
        public void setFamilyId(Long familyId) { this.familyId = familyId; }
        public int getMemberCount() { return memberCount; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
        public String getInviteText() { return inviteText; }
        public void setInviteText(String inviteText) { this.inviteText = inviteText; }
    }
}
