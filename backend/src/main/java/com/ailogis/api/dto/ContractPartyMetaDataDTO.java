package com.ailogis.api.dto;

public record ContractPartyMetaDataDTO(
        String legalName,
        String taxCode,
        String address,
        String phone,
        String email
) {}