package com.ailogis.api.dto;

public record ContractMetaDataResponseDTO(
        ContractPartyMetaDataDTO owner,
        ContractPartyMetaDataDTO renter
) {}