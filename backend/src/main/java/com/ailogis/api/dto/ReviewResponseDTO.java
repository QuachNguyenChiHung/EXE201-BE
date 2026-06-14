package com.ailogis.api.dto;

public record ReviewResponseDTO(
        Long id,
        String renterName,
        Integer rating,
        String comment
) {}