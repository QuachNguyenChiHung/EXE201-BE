package com.ailogis.api.dto;

public record OwnerStatisticResponseDTO(
        long totalWarehouses,
        double totalCapacity,
        double totalAvailable,
        double occupancyRate, // Tỷ lệ lấp đầy (%)
        long totalPendingRentRequests,
        long totalActiveContract,
        double billingThisMonth, // Tiền mua gói Sponsor tháng này
        long endingContract // Hợp đồng sắp hết hạn
) {}