package com.ailogis.api.dto;

public record PriceTierDTO(
        Long id,
        String label,     // VD: "Theo tháng"
        Double value,     // VD: 250000
        String unit,      // VD: "VND"
        String areaUnit   // VD: "m3"
) {}