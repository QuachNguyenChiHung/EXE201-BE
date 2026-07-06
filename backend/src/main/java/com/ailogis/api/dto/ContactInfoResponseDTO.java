package com.ailogis.api.dto;

public record ContactInfoResponseDTO(
        String renterPhone,
        String ownerPhone,
        String message
) {}