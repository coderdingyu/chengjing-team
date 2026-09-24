package com.chengjing.shared;

/** Shared HTTP envelope. Feature modules own their request, response and business types. */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "");
    }

    /**
     * Failure envelope. The shape stays {success,data,message} as fixed by docs/模块契约.md —
     * the HTTP status carries the error category, so no extra code field is added.
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
