package com.chengjing.preparation;

import org.springframework.http.HttpStatus;

/** B 模块的统一异常：带合适的 HTTP 状态码和中文提示，由 PreparationExceptionHandler 转成 ApiResponse。 */
public class PreparationException extends RuntimeException {
    private final HttpStatus status;

    public PreparationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static PreparationException badRequest(String message) {
        return new PreparationException(HttpStatus.BAD_REQUEST, message);
    }

    public static PreparationException forbidden(String message) {
        return new PreparationException(HttpStatus.FORBIDDEN, message);
    }

    public static PreparationException notFound(String message) {
        return new PreparationException(HttpStatus.NOT_FOUND, message);
    }

    public static PreparationException conflict(String message) {
        return new PreparationException(HttpStatus.CONFLICT, message);
    }

    public static PreparationException unavailable(String message) {
        return new PreparationException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
