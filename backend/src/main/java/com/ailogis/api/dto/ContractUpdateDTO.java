package com.ailogis.api.dto;

public record ContractUpdateDTO(
        Long totalPrice,
        String startAt,
        String endAt,
        String paymentTerm,
        String penaltyClause,
        String specialTerm,
        String cargoDescription,
        String ownerLegalName,
        String ownerTaxCode,
        String ownerAddress,
        String ownerPhone,
        String ownerEmail,
        String renterLegalName,
        String renterTaxCode,
        String renterAddress,
        String renterPhone,
        String renterEmail,
        String status
) {}