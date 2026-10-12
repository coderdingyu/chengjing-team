package com.chengjing.interview;

import com.chengjing.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Owner: member C (interview module).
 *
 * Keeps every interview endpoint on the shared {success,data,message} contract. Only this module's
 * packages are advised so other members keep control of their own error handling.
 */
@RestControllerAdvice(basePackages = "com.chengjing.interview")
public class InterviewExceptionHandler {

    @ExceptionHandler(InterviewException.class)
    public ResponseEntity<ApiResponse<Object>> handleInterview(InterviewException error) {
        return ResponseEntity.status(error.status())
                .body(new ApiResponse<>(false, null, error.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(Exception error) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, null, "请求内容无法解析，请检查字段格式"));
    }
}
