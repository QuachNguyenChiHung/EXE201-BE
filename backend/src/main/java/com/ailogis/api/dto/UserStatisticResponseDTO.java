package com.ailogis.api.dto;

import java.util.Map;

public record UserStatisticResponseDTO(Map<String, Long> byRole) {}