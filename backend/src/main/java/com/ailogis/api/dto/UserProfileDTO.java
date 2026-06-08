package com.ailogis.api.dto;

public record UserProfileDTO(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        String role,
        String status,
        CompanyResponseDTO company
) {}