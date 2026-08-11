package com.ailogis.api.dto;

public record OwnerStatisticResponseDTO(
        long totalWarehouses,
        double totalCapacity,
        double totalAvailable,
        double occupancyRate, // Tỷ lệ lấp đầy (%)
        long totalPendingRentRequests,
        long totalActiveContract,
        double billingThisMonth, // Tiền mua gói Sponsor tháng này
        long endingContract, // Hợp đồng sắp hết hạn
        long activeSponsorWarehouses, // Kho có gói Sponsor còn trong hạn thanh toán
        long sponsorsNeedingRenewal // Kho có gói Sponsor nhưng đã hết hạn, cần gia hạn
) {}