package com.ailogis.api.controller;

import com.ailogis.api.dto.FilterMetaResponseDTO;
import com.ailogis.api.dto.ReviewResponseDTO;
import com.ailogis.api.dto.WarehouseLocationDTO;
import com.ailogis.api.dto.WarehouseResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public ResponseEntity<Page<WarehouseResponseDTO>> getActiveOnly(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(warehouseService.getActiveOnlyWarehouses(PageRequest.of(page, size)));
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

    @GetMapping("/popular")
    public ResponseEntity<Page<WarehouseResponseDTO>> getPopularWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 12)); // Max 12 items
        return ResponseEntity.ok(warehouseService.getPopularWarehouses(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<WarehouseResponseDTO>> searchWarehouses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> provinces,
            @RequestParam(required = false) Double minArea,
            @RequestParam(required = false) Double maxArea,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(required = false) List<Long> certTypeIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(warehouseService.searchWarehouses(
                keyword, provinces, minArea, maxArea, minPrice, maxPrice, minRating, maxRating, certTypeIds, pageable));
    }

    @GetMapping("/filter-meta")
    public ResponseEntity<FilterMetaResponseDTO> getFilterMeta() {
        return ResponseEntity.ok(warehouseService.getFilterMeta());
    }

    @GetMapping("/{warehouseId}/reviews")
    public ResponseEntity<List<ReviewResponseDTO>> getWarehouseReviews(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(warehouseService.getWarehouseReviews(warehouseId));
    }
}