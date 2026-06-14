package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.FileStorageService;
import com.ailogis.api.service.OwnerService;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/owners")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;
    private final FileStorageService fileStorageService;
    private final WarehouseService warehouseService;

    // 1. Lấy danh sách kho bãi của CHÍNH Chủ kho đang đăng nhập
    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseResponseDTO>> getMyWarehouses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.getMyWarehouses(ownerId));
    }

    // 2. Xem các đơn yêu cầu thuê gửi tới các kho của mình
    @GetMapping("/requests")
    public ResponseEntity<List<RentRequestResponseDTO>> getIncomingRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.getRequestsForMyWarehouses(ownerId, status));
    }

    // 3. Duyệt hoặc từ chối đơn thuê
    @PatchMapping("/requests/{requestId}/status")
    public ResponseEntity<RentRequestResponseDTO> updateRequestStatus(
            @PathVariable Long requestId,
            @RequestBody RequestStatusUpdateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.updateRequestStatus(ownerId, requestId, dto));
    }

    // 4. Tạo kho bãi mới gắn thẳng vào ID của Chủ kho đang đăng nhập
    @PostMapping(value = "/warehouses", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WarehouseResponseDTO> createWarehouse(
            @RequestPart("warehouse") WarehouseCreateDTO dto,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestPart(value = "certificate", required = false) MultipartFile certificate,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();

        List<String> imageUrls = new java.util.ArrayList<>();
        String certificateUrl = null;

        if (images != null && images.length > 0) {
            for (MultipartFile img : images) {
                if (!img.isEmpty()) {
                    imageUrls.add(fileStorageService.storeFile(img, "images"));
                }
            }
        }

        if (certificate != null && !certificate.isEmpty()) {
            certificateUrl = fileStorageService.storeFile(certificate, "pdfs");
        }

        return ResponseEntity.ok(ownerService.createWarehouse(ownerId, dto, imageUrls, certificateUrl));
    }

    @GetMapping("/warehouses/{id}")
    public ResponseEntity<WarehouseResponseDTO> getMyWarehouseDetail(@PathVariable Long id) {
        // Chỉ lấy thông tin và thống kê phục vụ kiểm tra hệ thống, không tăng lượt xem
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }

    @GetMapping("/statistics")
    public ResponseEntity<OwnerStatisticResponseDTO> getOwnerStatistics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.getOwnerStatistics(ownerId));
    }

    @GetMapping("/warehouses/{id}/ratings")
    public ResponseEntity<WarehouseRatingResponseDTO> getMyWarehouseRatings(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.getWarehouseRatings(ownerId, id));
    }

    @PatchMapping("/warehouses/{id}/inactive")
    public ResponseEntity<WarehouseResponseDTO> deleteWarehouse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.deleteWarehouse(ownerId, id));
    }
}