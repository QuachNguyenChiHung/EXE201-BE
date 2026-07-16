package com.ailogis.api.dto;

import java.util.List;

public record RentRequestDetailResponseDTO(
                Long id,
                Integer sector,
                String priceTierLabel,
                Double priceTierValue,
                Double rentedArea,
                String areaUnit,
                Double tempMin,
                Double tempMax,
                Double humidity) {
}