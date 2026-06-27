package com.ailogis.api.dto;

public record CompanyResponseDTO(
        Long id,
        String companyName,
        String companyTaxCode
) {}