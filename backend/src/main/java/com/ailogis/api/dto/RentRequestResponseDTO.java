package com.ailogis.api.dto;

import java.util.List;

public record RentRequestResponseDTO(
        Long id,
        String warehouseName,
        String cargoDescription,
        Integer duration,
        String durationUnit,
        String status,
        String otherDetail,
        String renterRejectionReason,
        String rejectionReason,
        Double offeredPrice,
        String ownerNote,

        List<RentRequestDetailResponseDTO> details
) {}