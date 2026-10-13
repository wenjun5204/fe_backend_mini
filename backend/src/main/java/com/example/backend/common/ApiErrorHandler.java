package com.example.backend.common;

import com.example.backend.dto.ErrorDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一错误结构 {code, message},与契约 ErrorResponse 模型一致 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleOther(Exception e) {
        // 兑底异常必须打全量堆栈:线上排查依赖它(前端只见 50000,真相在日志)
        log.error("兑底异常 50000: {}", e.getMessage(), e);
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse("50000", "服务开小差了,请您稍后再试"));
    }
}
