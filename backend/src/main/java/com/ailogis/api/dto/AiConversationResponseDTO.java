package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record AiConversationResponseDTO(
        Long id,
        String messages,
        String criteria,
        Integer totalInputTokens,
        Integer totalOutputTokens,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
