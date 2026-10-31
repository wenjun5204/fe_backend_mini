package com.example.backend.common;

import com.example.backend.dto.ErrorDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 统一错误结构 {code, message},与契约 ErrorResponse 模型一致 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse(e.getCode(), e.getMessage()));
    }

    /** multipart 超过网关层大小闸(v1.9.2 照片上传 >1MB):归一 40000,与控制器层 512000 硬闸同文案同码 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse("40000", "照片太大，请压缩后再传"));
    }

    /** multipart 缺必填 part(v1.9.2 照片上传缺 image/scene 字段):归一 40000,与 JSON 形态缺失字段同码 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse("40000", "照片用途不对，请重新选择"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDtos.ErrorResponse> handleOther(Exception e) {
        // 兑底异常必须打全量堆栈:线上排查依赖它(前端只见 50000,真相在日志)
        log.error("兑底异常 50000: {}", e.getMessage(), e);
        return ResponseEntity.ok(new ErrorDtos.ErrorResponse("50000", "服务开小差了,请您稍后再试"));
    }
}
