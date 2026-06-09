package com.ailogis.api.dto;

import java.util.List;

public record OwnerDetailResponseDTO(
        UserDTO userInfo,
        List<WarehouseResponseDTO> warehouses,
        List<RentRequestResponseDTO> rentalRequests,
        List<ContractResponseDTO> contracts
) {}