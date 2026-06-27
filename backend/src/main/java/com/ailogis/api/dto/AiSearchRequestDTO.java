package com.ailogis.api.dto;

import java.util.List;

public record AiSearchRequestDTO(
    String query,
    String conversationHistory,
    Object criteria,
    List<Object> matchingWarehouses,
    Boolean isInitialHandshake
) {
    public AiSearchRequestDTO(String query) {
        this(query, null, null, null, null);
    }
}