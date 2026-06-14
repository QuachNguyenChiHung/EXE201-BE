package com.ailogis.api.dto;

public record CertificationSubmitDTO(
        Long id,
        String label,
        String link,
        String status,
        String rejectReason
) {}