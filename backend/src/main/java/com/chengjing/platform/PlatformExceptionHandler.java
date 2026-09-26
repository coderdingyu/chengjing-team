package com.chengjing.platform;

import com.chengjing.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.chengjing.platform")
public class PlatformExceptionHandler {
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PlatformException error) {
        return ResponseEntity.status(error.status()).body(new ApiResponse<>(false, null, error.getMessage()));
    }
}
