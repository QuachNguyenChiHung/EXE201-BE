package com.ailogis.api.dto;

import java.time.LocalDateTime;

public record RevenuePointDTO(
        String bucketLabel,
        LocalDateTime bucketStart,
        double renterAmount,
        double ownerAmount,
        double totalAmount
) {}
