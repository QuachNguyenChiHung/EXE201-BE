package com.ailogis.api.dto;

import java.util.List;

public record RentRequestResponseDTO(
        Long id,
        String warehouseName,
        String cargoDescription,
        Integer duration,
        String durationUnit,
        String status,
        List<RentRequestDetailResponseDTO> details
) {}