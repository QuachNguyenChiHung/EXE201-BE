package com.ailogis.api.controller;

import com.ailogis.api.dto.WarehouseLocationDTO;
import com.ailogis.api.dto.WarehouseResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PublicWarehouseController {
    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<List<WarehouseResponseDTO>> getActiveOnly() {
        return ResponseEntity.ok(warehouseService.getActiveOnlyWarehouses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponseDTO> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(warehouseService.getWarehouseDetailWithViewTracking(id, userDetails));
    }

    @GetMapping("/{id}/location")
    public ResponseEntity<WarehouseLocationDTO> getWarehouseLocation(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getWarehouseLocation(id));
    }
}