package com.example.backend.dto;

/**
 * 契约 ErrorResponse 模型:统一错误结构 {code, message}。
 * code 为字符串形式业务错误码,与既有 ApiException 及 v1.5 错误响应序列化一致。
 */
public final class ErrorDtos {

    private ErrorDtos() {
    }

    public static class ErrorResponse {

        private final String code;
        private final String message;

        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
