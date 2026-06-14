package com.ailogis.api.dto;

public record CertReviewDTO(
        String status,       // Gửi lên: "VERIFIED" hoặc "REJECTED"
        String rejectReason, // Bắt buộc nếu status = REJECTED
        Long typeId          // Chỉ cần thiết nếu status = VERIFIED
) {}