package com.ailogis.api.dto;

import java.util.List;

public record WarehouseSectionDTO(
        Long id,
        Integer sector,
        Double totalCapacity,
        Double availableCapacity,
        Double tempMin,
        Double tempMax,
        Double humidity,
        Boolean hasCertification,
        List<PriceTierDTO> priceTiers // 1 Phòng có nhiều biểu giá
) {}