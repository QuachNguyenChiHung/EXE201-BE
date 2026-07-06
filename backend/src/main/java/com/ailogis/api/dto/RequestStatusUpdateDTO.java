package com.ailogis.api.dto;

import com.ailogis.api.enums.RequestStatus;

public record RequestStatusUpdateDTO(
        RequestStatus status,
        String rejectionReason,
        String ownerNote
) {}