package com.chengjing.assessment;

import org.springframework.http.HttpStatus;

public class AssessmentException extends RuntimeException {
    private final HttpStatus status;

    public AssessmentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AssessmentException badRequest(String message) {
        return new AssessmentException(HttpStatus.BAD_REQUEST, message);
    }

    public static AssessmentException unavailable(String message) {
        return new AssessmentException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
