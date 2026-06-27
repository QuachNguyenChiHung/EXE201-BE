package com.ailogis.api.dto;

public record AiChatRequestDTO(
    String query,
    String conversationHistory
) {
    public AiChatRequestDTO(String query) {
        this(query, null);
    }
}
