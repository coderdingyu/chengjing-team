package com.chengjing.assessment;

import com.chengjing.shared.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.chengjing.assessment")
public class AssessmentExceptionHandler {
    @ExceptionHandler(AssessmentException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AssessmentException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(false, null, exception.getMessage()));
    }
}
