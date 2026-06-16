package com.ailogis.api.dto;

import java.util.List;

public record FilterMetaResponseDTO(
        List<String> locations,
        List<String> statuses,
        List<SponsorTierDTO> sponsorTiers,
        List<CertDTO> certifications
) {}