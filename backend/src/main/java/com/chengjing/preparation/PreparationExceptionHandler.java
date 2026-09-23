package com.chengjing.preparation;

import com.chengjing.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.chengjing.preparation")
public class PreparationExceptionHandler {
    @ExceptionHandler(PreparationException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PreparationException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(false, null, exception.getMessage()));
    }
}
