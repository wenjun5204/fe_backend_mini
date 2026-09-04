package com.example.backend.common;

/** 业务异常:code 遵循契约错误码(40000/40100/40300/40400/40900),message 面向用户且须过合规词表 */
public class ApiException extends RuntimeException {

    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ApiException param(String message) {
        return new ApiException("40000", message);
    }

    public static ApiException unauthorized() {
        return new ApiException("40100", "请先登录");
    }

    public static ApiException rateLimited(String message) {
        return new ApiException("40300", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException("40400", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException("40900", message);
    }
}
