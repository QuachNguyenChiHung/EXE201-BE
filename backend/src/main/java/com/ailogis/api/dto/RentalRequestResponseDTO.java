package com.ailogis.api.dto;

import java.time.LocalDate;

public record RentalRequestResponseDTO(
        Long id,
        String warehouseName,
        Integer requiredCapacity,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}