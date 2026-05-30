package com.ailogis.api.dto;

import java.util.List;

public record RentRequestDetailCreateDTO(
        Long sectionId,
        Long priceTierId,
        Double rentedArea,
        String areaUnit
) {}