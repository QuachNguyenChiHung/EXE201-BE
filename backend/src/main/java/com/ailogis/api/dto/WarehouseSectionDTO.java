package com.ailogis.api.dto;

import java.util.List;

public record WarehouseSectionDTO(
        Integer sector,
        Double totalCapacity,
        Double tempMin,
        Double tempMax,
        Double humidity,
        Boolean hasCertification,
        List<PriceTierDTO> priceTiers // 1 Phòng có nhiều biểu giá
) {}