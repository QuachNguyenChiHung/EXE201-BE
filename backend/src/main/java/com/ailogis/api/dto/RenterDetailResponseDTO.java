package com.ailogis.api.dto;

import java.util.List;

public record RenterDetailResponseDTO(
        UserProfileDTO userInfo,
        String aiSubscriptionPlan,
        List<RentRequestResponseDTO> rentalRequests,
        List<ContractResponseDTO> contracts,
        Double totalSpending
) {}