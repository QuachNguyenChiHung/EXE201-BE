package com.ailogis.api.dto;

import java.util.List;

public record AiFilterMetaResponseDTO(
        List<String> provinces,
        List<CertInfo> certifications,
        Double tempMin,
        Double tempMax,
        Double capacityMin,
        Double capacityMax,
        Double priceMin,
        Double priceMax,
        List<String> warehouseSectionLabels,
        List<String> priceTierLabels
) {
    public record CertInfo(String id, String label, String description) {}
}
