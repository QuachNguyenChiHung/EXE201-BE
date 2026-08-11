package com.ailogis.api.dto;

import java.util.List;

public record LoginResponseDTO(String token, String type, String email, String role, Long aiRenewalTierId,
        List<SponsorRenewalDTO> sponsorRenewals) {}