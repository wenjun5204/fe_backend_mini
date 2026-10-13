package com.example.backend.common;

/** 当前请求用户上下文(由 AuthInterceptor 写入) */
public final class CurrentUser {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    public static Long get() {
        Long id = USER_ID.get();
        if (id == null) {
            throw ApiException.unauthorized();
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
