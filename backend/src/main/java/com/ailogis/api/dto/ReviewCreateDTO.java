package com.ailogis.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewCreateDTO(
        @NotNull(message = "Số sao không được để trống")
        @Min(value = 1, message = "Đánh giá tối thiểu là 1 sao")
        @Max(value = 5, message = "Đánh giá tối đa là 5 sao")
        Integer rating,
        String comment
) {}