package com.ailogis.api.dto;

import java.util.List;

public record WarehouseCreateDTO(
        String name,
        String description,
        String locationAddressText,
        String locationProvince,
        String locationCommune,
        Double locationLong,
        Double locationLat,
        String locationPostalCode,
        List<WarehouseSectionDTO> sections // 1 Kho có nhiều phòng
) {}