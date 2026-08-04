package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record TransactionResponseDTO(
        Long id,
        Double amount,
        String type,
        String status,
        LocalDateTime createdAt,
        String providerTxnRef,
        String providerTransactionNo,
        String providerPayDate,
        String description
) {}