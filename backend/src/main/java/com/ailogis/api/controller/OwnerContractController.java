package com.ailogis.api.controller;

import com.ailogis.api.dto.ContractCreateDTO;
import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owners/contracts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OwnerContractController {

    private final ContractService contractService;

    @PostMapping
    public ResponseEntity<ContractResponseDTO> createContract(
            @RequestBody ContractCreateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(contractService.createContract(ownerId, dto));
    }

    @PatchMapping("/{contractId}/status")
    public ResponseEntity<ContractResponseDTO> updateStatus(
            @PathVariable Long contractId,
            @RequestParam com.ailogis.api.enums.ContractStatus status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(contractService.updateContractStatus(ownerId, contractId, status));
    }
}