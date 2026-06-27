package com.ailogis.api.dto;

public record SaveConversationRequestDTO(
    String messages,
    String criteria,
    Integer totalInputTokens,
    Integer totalOutputTokens,
    Integer warehouseCount
) {}
