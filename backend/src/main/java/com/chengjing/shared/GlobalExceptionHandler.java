package com.chengjing.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into the shared {success,data,message} envelope with a meaningful status.
 *
 * <p>Shared infrastructure: any module may throw {@link ApiException} and get a consistent
 * response. Internal failures are logged server-side and answered with a generic message so
 * no Java exception, stack trace or credential reaches the browser (docs/模块契约.md §1).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResponse.fail(ex.getMessage()));
    }

    /** Bean-validation failures answer with the first field message, which is written for users. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError field = ex.getBindingResult().getFieldError();
        String message = field == null || field.getDefaultMessage() == null
                ? "请检查填写的内容"
                : field.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("请求内容格式不正确"));
    }

    /**
     * Last resort. Spring's own protocol errors — an unknown route, a wrong method on a known
     * route, an unsupported content type — implement {@link ErrorResponse} and already carry the
     * right status, so they are answered with it. Reporting those as 500 would blame the server for
     * a client mistake and mislead whoever is integrating against the API (docs/模块契约.md §1).
     *
     * <p>Everything else is a genuine server fault: logged in full here, answered with a generic
     * message so no stack trace or internal detail reaches the browser.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status).body(ApiResponse.fail(describe(status)));
        }
        log.error("未预期的服务端错误", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("服务暂时未能完成请求，请稍后重试"));
    }

    private static String describe(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "接口不存在";
            case 405 -> "该接口不支持此请求方法";
            case 415 -> "请求内容类型不受支持";
            default -> "请求无法完成";
        };
    }
}
