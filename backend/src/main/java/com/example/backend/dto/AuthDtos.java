package com.example.backend.dto;

/** 鉴权契约模型:LoginRequest / LoginResponse */
public final class AuthDtos {

    private AuthDtos() {
    }

    public static class LoginRequest {
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class LoginResponse {
        private String token;
        private Long userId;
        private String nickname;
        private String avatarUrl;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }
}
