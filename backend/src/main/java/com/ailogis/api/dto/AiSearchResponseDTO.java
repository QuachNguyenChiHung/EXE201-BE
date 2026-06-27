package com.ailogis.api.dto;

import org.springframework.data.domain.Page;

public record AiSearchResponseDTO(
    String response,
    Page<WarehouseResponseDTO> warehouses,
    java.util.List<Long> refinedWarehouseIds,
    String criteriaJson,
    Integer inputTokens,
    Integer outputTokens
) {}