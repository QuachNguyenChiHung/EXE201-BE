package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record HighestTransactionDTO(
        Long id,
        Double amount,
        String type,
        String status,
        Long buyerId,
        String buyerName,
        String buyerRole,
        LocalDateTime createdAt
) {}
