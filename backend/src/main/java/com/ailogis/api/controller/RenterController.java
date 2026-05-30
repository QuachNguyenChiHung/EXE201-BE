package com.ailogis.api.controller;

import com.ailogis.api.dto.RentRequestCreateDTO;
import com.ailogis.api.dto.RentRequestResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.RentalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/renters")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RenterController {
    private final RentalRequestService requestService;

    @PostMapping("/requests")
    public ResponseEntity<RentRequestResponseDTO> createRequest(
            @RequestBody RentRequestCreateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(requestService.createRequest(renterId, dto));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<RentRequestResponseDTO>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long renterId = userDetails.getUser().getId();
        return ResponseEntity.ok(requestService.getRequestsByRenter(renterId));
    }
}