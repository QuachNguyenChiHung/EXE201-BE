package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record NotificationResponseDTO(
        Long id,
        String message,
        LocalDateTime createdAt,
        Boolean read
) {}
