package com.ailogis.api.dto;

public record AiTierDTO(
        Long id,
        String label,
        String description,
        Integer tokenInput,
        Integer tokenOutput,
        Double price,
        String unit,
        Long activeUsersCount // Thống kê số lượng người dùng đang đăng ký
) {}