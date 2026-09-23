package com.example.backend.dto;

/** 个人档案相关 DTO(挂件佩戴)。 */
public class MeDtos {

    public static class PendantWearRequest {
        private String pendant;

        public String getPendant() { return pendant; }
        public void setPendant(String pendant) { this.pendant = pendant; }
    }

    public static class PendantWearResponse {
        private String pendant;

        public String getPendant() { return pendant; }
        public void setPendant(String pendant) { this.pendant = pendant; }
    }
}
