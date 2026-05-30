package com.ailogis.api.dto;

public record CertificationSubmitDTO(
        Long id,
        String label,
        String link,
        Boolean isVerified
) {}