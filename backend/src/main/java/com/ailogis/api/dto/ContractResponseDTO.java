package com.ailogis.api.dto;

import java.time.LocalDate;

public record ContractResponseDTO(
        Long id,
        Long requestId,
        String warehouseName,
        String renterName,
        Long totalPrice,
        LocalDate signedDate,
        String status
) {}