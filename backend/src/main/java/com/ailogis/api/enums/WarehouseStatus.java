package com.ailogis.api.enums;

public enum WarehouseStatus {
    // Trạng thái duyệt
    PENDING,
    REJECTED,

    // Trạng thái vận hành
    ACTIVE,
    RENTED, // Khi kho đã được thuê và các section đã được đặt hết
    INACTIVE,
}