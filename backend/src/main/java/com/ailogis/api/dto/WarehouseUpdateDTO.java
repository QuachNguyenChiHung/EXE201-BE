package com.ailogis.api.dto;

import java.util.List;

public record WarehouseUpdateDTO(
        String name,
        String description,
        String locationAddressText,
        String locationProvince,
        String locationCommune,
        List<WarehouseSectionUpdateDTO> sections
) {}