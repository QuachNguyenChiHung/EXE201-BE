package com.ailogis.api.dto;

import java.time.LocalDate;

public record ContractResponseDTO(
        Long id,
        Long requestId,
        String warehouseName,
        String cargoDescription,
        LocalDate startAt,
        LocalDate endAt,
        String paymentTerm,
        String penaltyClause,
        String specialTerm,
        String cancelReason,

        // Thông tin pháp lý Bên A (Owner)
        String ownerLegalName,
        String ownerTaxCode,
        String ownerEmail,
        String ownerPhone,
        String ownerAddress,

        // Thông tin pháp lý Bên B (Renter)
        String renterLegalName,
        String renterTaxCode,
        String renterEmail,
        String renterPhone,
        String renterAddress,

        Long totalPrice,
        String status
) {}