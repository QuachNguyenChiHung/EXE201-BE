package com.ailogis.api.controller;

import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.dto.RentRequestCreateDTO;
import com.ailogis.api.dto.RentRequestResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.ContractService;
import com.ailogis.api.service.RentalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/renters")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RenterController {
    private final RentalRequestService requestService;
    private final ContractService contractService;

    @PostMapping("/requests")
    public ResponseEntity<RentRequestResponseDTO> createRequest(
            @RequestBody RentRequestCreateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(requestService.createRequest(renterId, dto));
    }

    @GetMapping("/requests")
    public ResponseEntity<Page<RentRequestResponseDTO>> getMyRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(requestService.getRequestsByRenter(renterId, status, pageable));
    }

    @PatchMapping("/requests/{id}/cancel")
    public ResponseEntity<RentRequestResponseDTO> cancelRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(requestService.cancelRequestByRenter(userDetails.getUser().getId(), id, reason));
    }

    @PatchMapping("/contracts/{id}/sign")
    public ResponseEntity<ContractResponseDTO> signContract(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(contractService.signContract(renterId, id));
    }

    @PatchMapping("/contracts/{id}/reject")
    public ResponseEntity<ContractResponseDTO> rejectContract(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        String reason = body != null ? body.get("reason") : null;

        return ResponseEntity.ok(contractService.rejectContract(renterId, id, reason));
    }
}