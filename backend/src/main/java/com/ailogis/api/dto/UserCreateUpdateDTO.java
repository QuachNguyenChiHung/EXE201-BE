package com.ailogis.api.dto;

public record UserCreateUpdateDTO(
        String name,
        String email,
        String password,
        String phone,
        String role,
        String status,
        String imgLink,
        String hashTaxCode,
        Long aiTier,
        Long companyID
) {}