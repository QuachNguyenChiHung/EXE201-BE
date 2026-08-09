package com.ailogis.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Chuẩn hóa response lỗi trả về cho FE. Các trường bổ sung
 * ({@code remainingAttempts}, {@code lockUntil}) là optional — chỉ xuất hiện
 * trong body khi handler bổ sung, đảm bảo tương thích ngược với client cũ.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        Integer remainingAttempts,
        LocalDateTime lockUntil
) {
    /** Factory cho response lỗi đơn giản (không kèm remainingAttempts / lockUntil). */
    public static ErrorResponseDTO of(int status, String error, String message) {
        return new ErrorResponseDTO(status, error, message, LocalDateTime.now(), null, null);
    }

    /** Factory cho response lỗi kèm remainingAttempts và lockUntil (dùng cho 401 / 423). */
    public static ErrorResponseDTO of(int status, String error, String message,
                                      Integer remainingAttempts, LocalDateTime lockUntil) {
        return new ErrorResponseDTO(status, error, message, LocalDateTime.now(),
                remainingAttempts, lockUntil);
    }
}
