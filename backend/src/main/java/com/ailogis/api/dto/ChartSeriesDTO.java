package com.ailogis.api.dto;

import java.util.List;

public record ChartSeriesDTO(
        String role,
        List<Long> data
) {}