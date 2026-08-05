package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record EmployeeTransactionDTO(
        Long id,
        Long buyerId,
        String buyerName,
        String buyerEmail,
        String buyerRole,
        String type,
        String status,
        Double amount,
        LocalDateTime createdAt,
        LocalDateTime invoiceDate,
        String description
) {}
