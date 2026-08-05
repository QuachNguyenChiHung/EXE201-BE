package com.ailogis.api.dto;

import java.util.List;

public record TransactionAnalyticsSummaryDTO(
        List<TransactionTypeShareDTO> revenueByType,
        String topServiceType,
        double topServiceRevenue,
        String mostCommonType,
        long mostCommonTypeCount,
        HighestTransactionDTO highestTransaction,
        String topSpendingRole,
        double topSpendingRoleAmount
) {}
