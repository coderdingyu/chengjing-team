package com.chengjing.shared;

/** Shared HTTP envelope. Feature modules own their request, response and business types. */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "");
    }
}
