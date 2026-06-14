package com.ailogis.api.dto;

import java.util.List;

public record WarehouseSectionUpdateDTO(
        Long id, // Truyền id nếu là update phòng cũ, null nếu là tạo phòng mới
        Integer sector,
        Double totalCapacity,
        Double tempMin,
        Double tempMax,
        Double humidity,
        Boolean hasCertification,
        List<PriceTierDTO> priceTiers // Danh sách giá mới (nếu có cập nhật)
) {}