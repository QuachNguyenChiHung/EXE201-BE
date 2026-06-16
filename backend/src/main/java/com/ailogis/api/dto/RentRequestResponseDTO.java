package com.ailogis.api.dto;

import java.util.List;

public record RentRequestResponseDTO(
        Long id,
        String warehouseName,
        String renterName,
        String ownerName,
        String cargoDescription,
        Integer duration,
        String durationUnit,
        String status,
        String otherDetail,
        String renterRejectionReason,
        String rejectionReason,
        Double offeredPrice,
        Double renterOfferedPrice,
        String ownerNote,

        List<RentRequestDetailResponseDTO> details
) {}