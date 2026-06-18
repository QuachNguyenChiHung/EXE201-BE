package com.ailogis.api.dto;

import java.time.LocalDate;
import java.util.List;

public record RentRequestCreateDTO(
        Long warehouseId,
        String cargoDescription,
        String otherDetail,
        Integer duration,
        String durationUnit,
        LocalDate startDate,
        LocalDate endDate,
        Double renterOfferedPrice,
        List<RentRequestDetailCreateDTO> details // Thuê nhiều phòng cùng lúc
) {}