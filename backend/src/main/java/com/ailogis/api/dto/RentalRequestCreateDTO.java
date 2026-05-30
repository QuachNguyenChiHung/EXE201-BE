package com.ailogis.api.dto;

import java.time.LocalDate;

public record RentalRequestCreateDTO(
        Long warehouseId,
        Integer requiredCapacity,
        LocalDate startDate,
        LocalDate endDate,
        String productType
) {}