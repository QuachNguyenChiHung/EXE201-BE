package com.ailogis.api.dto;

import com.ailogis.api.enums.RequestStatus;

public record RequestStatusUpdateDTO(
        RequestStatus status,
        Double offeredPrice,          // Mức giá mới (nếu Owner muốn đổi giá)
        String rejectionReason,
        String ownerNote
) {}