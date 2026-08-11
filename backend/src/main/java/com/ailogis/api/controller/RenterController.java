package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.ContractService;
import com.ailogis.api.service.RentalRequestService;
import com.ailogis.api.service.RenterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final RenterService renterService;

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

    @GetMapping("/requests/{id}/contact")
    public ResponseEntity<ContactInfoResponseDTO> getContactInfo(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(requestService.getContactInfo(id, renterId));
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

    @PatchMapping("/contracts/{id}/cancel")
    public ResponseEntity<ContractResponseDTO> cancelContract(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(contractService.cancelContract(renterId, id, reason));
    }

    @GetMapping("/statistics")
    public ResponseEntity<RenterStatisticResponseDTO> getDashboardStatistics(
            @RequestParam(defaultValue = "30") int expireDays,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(renterService.getRenterStatistics(renterId, expireDays));
    }

    @GetMapping("/ai-tiers")
    public ResponseEntity<List<AiTierDTO>> getAiTiers() {
        return ResponseEntity.ok(renterService.getActiveAiTiers());
    }

    @PostMapping("/ai-tiers/{id}/pay")
    public ResponseEntity<PaymentResponseDTO> processPaymentForAI(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean immediate,
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(renterService.buyAiSubscription(renterId, id, request, immediate));
    }

    @DeleteMapping("/ai-subscription")
    public ResponseEntity<Void> cancelAiSubscription(@AuthenticationPrincipal CustomUserDetails userDetails) {
        renterService.cancelAiSubscription(userDetails.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bookmarks/{warehouseId}")
    public ResponseEntity<String> toggleBookmark(
            @PathVariable Long warehouseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(renterService.toggleBookmark(userDetails.getUser().getId(), warehouseId));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<List<WarehouseResponseDTO>> getMyBookmarks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(renterService.getMyBookmarks(userDetails.getUser().getId()));
    }

    @PostMapping("/warehouses/{warehouseId}/ratings")
    public ResponseEntity<ReviewResponseDTO> rateWarehouse(
            @PathVariable Long warehouseId,
            @Valid @RequestBody ReviewCreateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(renterService.createReview(renterId, warehouseId, dto));
    }

    @PostMapping("/requests/{id}/pay")
    public ResponseEntity<PaymentResponseDTO> payForRentalRequest(
            @PathVariable Long id,
            HttpServletRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(renterService.payForRentalRequest(renterId, id, request));
    }
}