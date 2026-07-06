package com.ailogis.api.dto;

import java.time.LocalDate;
import java.util.List;

public record RentRequestResponseDTO(
        Long id,
        Long warehouseId,
        String warehouseName,
        String renterName,
        String renterCompanyName,
        String renterCompanyTaxCode,
        String ownerName,
        String cargoDescription,
        Integer duration,
        String durationUnit,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String otherDetail,
        String renterRejectionReason,
        String rejectionReason,
        Double offeredPrice,
        String ownerNote,
        String renterNote,

        List<RentRequestDetailResponseDTO> details
) {}