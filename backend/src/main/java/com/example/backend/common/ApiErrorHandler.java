package com.example.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一错误结构 {code, message},与契约错误码一致 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", e.getCode());
        body.put("message", e.getMessage());
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        // 兑底异常必须打全量堆栈:线上排查依赖它(前端只见 50000,真相在日志)
        log.error("兑底异常 50000: {}", e.getMessage(), e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "50000");
        body.put("message", "服务开小差了,请您稍后再试");
        return ResponseEntity.ok(body);
    }
}
