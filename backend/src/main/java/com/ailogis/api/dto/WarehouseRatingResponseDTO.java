package com.ailogis.api.dto;

import java.util.List;

public record WarehouseRatingResponseDTO(
        Double averageRating,
        Integer totalReviews,
        List<ReviewResponseDTO> reviews
) {}