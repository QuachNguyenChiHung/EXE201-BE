package com.ailogis.api.dto;

import java.util.List;

public record WarehouseUpdateDTO(
        String name,
        String description,
        String locationAddressText,
        String locationProvince,
        String locationCommune,
        Double locationLong,
        Double locationLat,
        List<WarehouseSectionUpdateDTO> sections
) {}