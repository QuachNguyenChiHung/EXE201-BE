package com.ailogis.api.dto;

public record UserProfileUpdateDTO(
        String fullName,
        String phone,
        String dateOfBirth,
        String gender,
        String companyName,
        String companyTaxCode
) {}