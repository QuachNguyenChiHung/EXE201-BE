package com.ailogis.api.dto;

import java.util.List;

public record WarehouseDTO(
        Long id,
        String name,
        String city,
        String province,
        Double temperatureMin,
        Double temperatureMax,
        Integer availableCapacity,
        Integer pricePerM3Month,
        String securityLevel,
        List<String> certifications, // Đã được tách từ chuỗi "haccp,iso"
        List<String> features       // Đã được tách từ chuỗi "port,24h"
) {}