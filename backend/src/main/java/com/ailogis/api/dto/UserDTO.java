package com.ailogis.api.dto;

public record UserDTO(
        Long id,
        String email,
        String fullName,
        String companyName,
        String role,
        String status
) {}