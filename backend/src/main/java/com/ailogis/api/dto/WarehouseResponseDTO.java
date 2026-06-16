package com.ailogis.api.dto;

import java.util.List;
import java.util.Map;

public record WarehouseResponseDTO(
        Long id,
        String name,
        String description,
        String locationAddressText,
        String locationProvince,
        String locationCommune,
        List<WarehouseSectionDTO> sections,
        List<WarehouseImageDTO> images,
        List<CertificationSubmitDTO> certificates,
        String status,
        Map<String, Long>viewCountByDate,
        Long pendingRequestCount,
        Boolean isSponsor,
        SponsorTierDTO sponsorTier
) {}