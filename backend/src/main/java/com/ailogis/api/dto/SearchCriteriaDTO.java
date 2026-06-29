package com.ailogis.api.dto;

import java.util.List;

public record SearchCriteriaDTO(
    List<LocationDTO> location,
    Double minPrice,
    Double maxPrice,
    List<String> priceType,
    String areaUnit,
    String name,
    Double tempMin,
    Double tempMax,
    CapacityRange availableCapacity,
    CapacityRange totalCapacity,
    RatingRange rating,
    List<String> certificates,
    SortType sort,
    List<String> warehouseSection,
    String priceTier
) {
    public record LocationDTO(String province) {}

    public record CapacityRange(Double min_range, Double max_range) {}

    public record RatingRange(Double min_range, Double max_range) {}

    public record SortType(String type) {}
}
