package com.ailogis.api.dto;

public record SponsorTierDTO(
        Long id,
        Integer priorityLevel,
        Double pricingPerMonth,
        Double yearPackSale,
        String label,
        Long activeWarehousesCount // Thống kê số lượng kho bãi đang chạy gói tài trợ này
) {}