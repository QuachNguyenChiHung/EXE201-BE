package com.ailogis.api.dto;

import java.util.List;

public record RentRequestCreateDTO(
        Long warehouseId,
        String cargoDescription,
        String otherDetail,
        Integer duration,
        String durationUnit,
        List<RentRequestDetailCreateDTO> details // Thuê nhiều phòng cùng lúc
) {}