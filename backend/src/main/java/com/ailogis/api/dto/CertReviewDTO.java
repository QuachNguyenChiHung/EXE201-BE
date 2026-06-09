package com.ailogis.api.dto;

public record CertReviewDTO(
        Boolean isVerified,
        Long typeId         // ID của loại chứng chỉ (VD: 1 cho HACCP, 2 cho ISO) - Gán khi isVerified = true
) {}