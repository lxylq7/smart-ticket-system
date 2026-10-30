package com.example.ticket.common.response;

import java.time.Instant;

/** Shared JSON envelope. HTTP status codes continue to describe success/failure. */
public record ApiResponse<T>(String code, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }
}
