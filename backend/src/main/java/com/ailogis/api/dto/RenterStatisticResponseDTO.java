package com.ailogis.api.dto;

public record RenterStatisticResponseDTO(
        Long totalWarehouseWithActiveContract,
        Long totalAiConversation,
        Long totalTokenUsage,
        Double totalBilling,
        String aiSubscriptionInUse,
        Long totalRentRequest,
        Long totalOwnerUpdatedRequest,
        Long totalActiveContract,
        Long endOfContract,
        String activeAiTierLabel
) {}