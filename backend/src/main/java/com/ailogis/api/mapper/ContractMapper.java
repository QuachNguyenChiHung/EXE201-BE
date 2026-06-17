package com.ailogis.api.mapper;

import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.entity.Contract;
import org.springframework.stereotype.Component;

@Component
public class ContractMapper {

    public ContractResponseDTO toContractResponseDTO(Contract c) {
        if (c == null) {
            return null;
        }

        return new ContractResponseDTO(
                c.getId(),
                c.getRequest() != null ? c.getRequest().getId() : null,
                (c.getRequest() != null && c.getRequest().getWarehouse() != null) ? c.getRequest().getWarehouse().getName() : "N/A",
                c.getCargoDescription(),
                c.getStartAt(),
                c.getEndAt(),
                c.getPaymentTerm(),
                c.getPenaltyClause(),
                c.getSpecialTerm(),
                c.getCancelReason(),
                c.getOwnerSigned(),
                c.getRenterSigned(),

                c.getOwnerLegalName(),
                c.getOwnerTaxCode(),
                c.getOwnerEmail(),
                c.getOwnerPhone(),
                c.getOwnerAddress(),

                c.getRenterLegalName(),
                c.getRenterTaxCode(),
                c.getRenterEmail(),
                c.getRenterPhone(),
                c.getRenterAddress(),

                c.getTotalPrice(),
                c.getStatus() != null ? c.getStatus().name() : null
        );
    }
}