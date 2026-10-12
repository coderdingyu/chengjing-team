package com.chengjing.interview;

import org.springframework.http.HttpStatus;

/**
 * Owner: member C (interview module).
 *
 * Business failures carry an explicit HTTP status so the shared envelope can answer with a
 * readable message instead of a stack trace or an internal payload.
 */
public class InterviewException extends RuntimeException {

    private final HttpStatus status;

    private InterviewException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static InterviewException badRequest(String message) {
        return new InterviewException(HttpStatus.BAD_REQUEST, message);
    }

    public static InterviewException unauthorized(String message) {
        return new InterviewException(HttpStatus.UNAUTHORIZED, message);
    }

    public static InterviewException forbidden(String message) {
        return new InterviewException(HttpStatus.FORBIDDEN, message);
    }

    public static InterviewException notFound(String message) {
        return new InterviewException(HttpStatus.NOT_FOUND, message);
    }

    public static InterviewException conflict(String message) {
        return new InterviewException(HttpStatus.CONFLICT, message);
    }
}
