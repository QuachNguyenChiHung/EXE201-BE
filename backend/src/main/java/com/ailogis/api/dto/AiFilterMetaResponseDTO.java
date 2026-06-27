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
        Double priceMax
) {
    public record CertInfo(String id, String label, String description) {}
}
