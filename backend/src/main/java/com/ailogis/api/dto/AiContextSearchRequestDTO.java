package com.ailogis.api.dto;

import java.util.List;

public record AiContextSearchRequestDTO(
    String query,
    String conversationHistory,
    List<Object> warehouses
) {}
