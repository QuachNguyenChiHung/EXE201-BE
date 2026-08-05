package com.ailogis.api.dto;

public record TransactionTypeShareDTO(
        String type,
        double totalAmount,
        long count,
        double percentage
) {}
