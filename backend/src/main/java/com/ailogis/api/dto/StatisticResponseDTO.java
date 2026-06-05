package com.ailogis.api.dto;

import java.util.Map;

public record StatisticResponseDTO(
        long usersCount,
        Map<String, Long> usersByRole,
        Map<String, Long> warehousesByStatus,
        Map<String, Long> rentRequestsByStatus,
        Map<String, Long> contractsByStatus
) {}