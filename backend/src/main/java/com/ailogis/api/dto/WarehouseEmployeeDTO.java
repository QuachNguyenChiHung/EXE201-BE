package com.ailogis.api.dto;

import java.util.List;
import java.util.Map;

public record WarehouseEmployeeDTO(
        Long id_warehouse,
        Long id_owner,
        String name,
        String address,
        String location_commune,
        String location_province,
        String status,
        String ownerName,
        Double pricePerCubicMeter,
        Map<String, Object> stats,
        List<Map<String, Object>> sections,
        List<CertificationSubmitDTO> certifications
) {}