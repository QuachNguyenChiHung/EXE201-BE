package com.ailogis.api.dto;

public record UserProfileUpdateDTO(
        String fullName,
        String phone,
        String companyName,
        String companyTaxCode
) {}