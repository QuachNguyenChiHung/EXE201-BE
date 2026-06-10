package com.ailogis.api.controller;

import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.dto.RentRequestResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.ContractService;
import com.ailogis.api.service.RentalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SharedResourceController {

    private final RentalRequestService requestService;
    private final ContractService contractService;

    // View Chi tiết Request (Áp dụng verify)
        @GetMapping("/requests/{id}")
    public ResponseEntity<RentRequestResponseDTO> getRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(requestService.getRequestDetail(id, userDetails));
    }

    // View Chi tiết Contract (Áp dụng verify)
    @GetMapping("/contracts/{id}")
    public ResponseEntity<ContractResponseDTO> getContractDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(contractService.getContractDetail(id, userDetails));
    }

    @GetMapping("/contracts")
    public ResponseEntity<List<ContractResponseDTO>> getMyContracts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(contractService.getMyContracts(userDetails));
    }
}