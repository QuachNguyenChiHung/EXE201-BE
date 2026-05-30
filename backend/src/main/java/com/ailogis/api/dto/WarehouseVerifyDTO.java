package com.ailogis.api.dto;

import com.ailogis.api.enums.WarehouseStatus;

public record WarehouseVerifyDTO(
        WarehouseStatus status // Truyền APPROVED hoặc REJECTED
) {}