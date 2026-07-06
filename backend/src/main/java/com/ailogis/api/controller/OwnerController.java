package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.FileStorageService;
import com.ailogis.api.service.OwnerService;
import com.ailogis.api.service.RentalRequestService;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owners")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;
    private final FileStorageService fileStorageService;
    private final WarehouseService warehouseService;
    private final RentalRequestService rentalRequestService;

    // 1. Lấy danh sách kho bãi của CHÍNH Chủ kho đang đăng nhập
    @GetMapping("/warehouses")
    public ResponseEntity<Page<WarehouseResponseDTO>> getMyWarehouses(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ownerService.getMyWarehouses(userDetails.getUser().getId(), status, PageRequest.of(page, size)));
    }

    // 2. Xem các đơn yêu cầu thuê gửi tới các kho của mình
    @GetMapping("/requests")
    public ResponseEntity<Page<RentRequestResponseDTO>> getIncomingRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ownerService.getRequestsForMyWarehouses(userDetails.getUser().getId(), status, PageRequest.of(page, size)));
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
    @PostMapping(value = "/warehouses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WarehouseResponseDTO> createWarehouse(
            @RequestPart("warehouse") WarehouseCreateDTO dto,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "certTypeIds", required = false) List<Long> certTypeIds,
            @RequestPart(value = "certFiles", required = false) MultipartFile[] certFiles,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        List<String> imageUrls = new ArrayList<>();
        List<WarehouseCertCreateDTO> certificates = new ArrayList<>();

        // 1. Xử lý Upload Hình ảnh
        if (images != null && images.length > 0) {
            for (MultipartFile img : images) {
                if (!img.isEmpty()) {
                    imageUrls.add(fileStorageService.storeFile(img, "warehouses"));
                }
            }
        }

        // 2. Xử lý Upload Chứng chỉ (Nhiều file kèm theo Loại)
        if (certFiles != null && certTypeIds != null && certFiles.length == certTypeIds.size()) {
            for (int i = 0; i < certFiles.length; i++) {
                MultipartFile certFile = certFiles[i];
                if (!certFile.isEmpty()) {
                    String certUrl = fileStorageService.storeFile(certFile, "certs");
                    certificates.add(new WarehouseCertCreateDTO(certTypeIds.get(i), certUrl));
                }
            }
        }

        return ResponseEntity.ok(ownerService.createWarehouse(ownerId, dto, imageUrls, certificates));
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

    @PatchMapping("/warehouses/{id}/active")
    public ResponseEntity<WarehouseResponseDTO> reactivateWarehouse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.reactivateWarehouse(ownerId, id));
    }

    @PutMapping(value = "/warehouses/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WarehouseResponseDTO> updateWarehouse(
            @PathVariable Long id,
            @RequestPart("warehouse") WarehouseUpdateDTO dto,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "deletedImageIds", required = false) List<Long> deletedImageIds,
            @RequestParam(value = "certTypeIds", required = false) List<Long> certTypeIds,
            @RequestPart(value = "certFiles", required = false) MultipartFile[] certFiles,
            @RequestParam(value = "deletedCertIds", required = false) List<Long> deletedCertIds,
            @RequestParam(required = false) Boolean force,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long ownerId = userDetails.getUser().getId();
        List<String> newImageUrls = new ArrayList<>();
        List<WarehouseCertCreateDTO> newCertificates = new ArrayList<>();

        // 1. Upload Hình ảnh mới
        if (images != null && images.length > 0) {
            for (MultipartFile img : images) {
                if (!img.isEmpty()) {
                    newImageUrls.add(fileStorageService.storeFile(img, "warehouses"));
                }
            }
        }

        // 2. Upload Chứng chỉ mới
        if (certFiles != null && certTypeIds != null && certFiles.length == certTypeIds.size()) {
            for (int i = 0; i < certFiles.length; i++) {
                MultipartFile certFile = certFiles[i];
                if (!certFile.isEmpty()) {
                    String certUrl = fileStorageService.storeFile(certFile, "certs");
                    newCertificates.add(new WarehouseCertCreateDTO(certTypeIds.get(i), certUrl));
                }
            }
        }

        return ResponseEntity.ok(ownerService.updateWarehouse(ownerId, id, dto, force, newImageUrls, deletedImageIds, newCertificates, deletedCertIds));
    }

    @PostMapping("/warehouses/{id}/sponsor")
    public ResponseEntity<PaymentResponseDTO> buySponsorTier(
            @PathVariable Long id,
            @RequestBody BuySponsorRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            jakarta.servlet.http.HttpServletRequest request) {

        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.buySponsorTier(ownerId, id, dto, request));
    }

    // Lấy danh sách yêu cầu thuê của riêng 1 kho bãi
    @GetMapping("/warehouses/{warehouseId}/requests")
    public ResponseEntity<Page<RentRequestResponseDTO>> getWarehouseRentRequests(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ownerService.getWarehouseRentRequests(userDetails.getUser().getId(), warehouseId, status, PageRequest.of(page, size)));
    }

    // Lấy danh sách hợp đồng của riêng 1 kho bãi
    @GetMapping("/warehouses/{warehouseId}/contracts")
    public ResponseEntity<Page<ContractResponseDTO>> getWarehouseContracts(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ownerService.getWarehouseContracts(userDetails.getUser().getId(), warehouseId, status, PageRequest.of(page, size)));
    }

    @GetMapping("/sponsor-tiers")
    public ResponseEntity<List<SponsorTierDTO>> getActiveSponsorTiers(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(ownerService.getSponsorTiersForOwner(ownerId));
    }

    @PutMapping("/requests/{requestId}/accept")
    public ResponseEntity<ContactInfoResponseDTO> acceptRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        return ResponseEntity.ok(rentalRequestService.acceptRentalRequest(ownerId, requestId));
    }

    @PutMapping("/requests/{requestId}/reject")
    public ResponseEntity<String> rejectRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long ownerId = userDetails.getUser().getId();
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Chủ kho từ chối yêu cầu";

        rentalRequestService.rejectRentalRequest(ownerId, requestId, reason);
        return ResponseEntity.ok("Đã từ chối yêu cầu thuê và tự động hoàn tiền cho Renter qua VNPay.");
    }
}