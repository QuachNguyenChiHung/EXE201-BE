package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.service.EmployeeService;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final WarehouseService warehouseService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(employeeService.searchUsers(null, role, PageRequest.of(page, size)));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<Page<WarehouseEmployeeDTO>> getAllWarehouses(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        WarehouseStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = WarehouseStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái kho bãi không hợp lệ!");
            }
        }
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(statusEnum, PageRequest.of(page, size)));
    }

    @PatchMapping("/warehouses/{id}/accept")
    public ResponseEntity<WarehouseEmployeeDTO> acceptWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.changeWarehouseStatus(id, WarehouseStatus.ACTIVE));
    }

    @PatchMapping("/warehouses/{id}/rejected")
    public ResponseEntity<WarehouseEmployeeDTO> rejectWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.changeWarehouseStatus(id, WarehouseStatus.REJECTED));
    }

    @GetMapping("/statistic")
    public ResponseEntity<StatisticResponseDTO> getGlobalStatistics() {
        return ResponseEntity.ok(employeeService.getGlobalStatistics());
    }

    @GetMapping("/users-statistic")
    public ResponseEntity<UserStatisticResponseDTO> getUsersStatistics() {
        return ResponseEntity.ok(employeeService.getUsersStatistics());
    }

    /*
        Mặc định (30 ngày): GET /api/employees/users/active-count
        Xem 1 ngày: GET /api/employees/users/active-count?days=1
        Xem 1 tuần: GET /api/employees/users/active-count?days=7
     */
    @GetMapping("/users/active-count")
    public ResponseEntity<Map<String, Object>> getActiveUsersCount(
            @RequestParam(defaultValue = "30") int days) {

        long count = employeeService.getActiveUsersCount(days);

        Map<String, Object> response = new HashMap<>();
        response.put("days", days);
        response.put("activeUsersCount", count);
        response.put("message", "Số người dùng active trong " + days + " ngày qua");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/activity-stats")
    public ResponseEntity<Map<String, Object>> getUserActivityStats(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") int days) {

        return ResponseEntity.ok(employeeService.getUserActivityStats(userId, days));
    }

    @GetMapping("/warehouses/{id}")
    public ResponseEntity<WarehouseResponseDTO> getWarehouseDetailForEmployee(@PathVariable Long id) {
        // Chỉ lấy thông tin và thống kê phục vụ kiểm tra hệ thống, không tăng lượt xem
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }

    @PatchMapping("/certifications/{submitId}/review")
    public ResponseEntity<CertificationSubmitDTO> reviewCertification(
            @PathVariable Long submitId,
            @RequestBody CertReviewDTO dto) {
        return ResponseEntity.ok(employeeService.reviewWarehouseCertification(submitId, dto));
    }

    // Thống kê theo Role qua các ngày
    @GetMapping("/statistics/active-users/by-date")
    public ResponseEntity<Map<String, Object>> getActiveUsersByDate(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return ResponseEntity.ok(employeeService.getActiveUsersByDate(start, end));
    }

    // Thống kê theo giờ trong 1 ngày
    @GetMapping("/statistics/active-users/by-hour")
    public ResponseEntity<Map<String, Object>> getActiveUsersByHour(
            @RequestParam(required = false) String date) {
        LocalDate queryDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(employeeService.getActiveUsersByHour(queryDate));
    }

    // Chi tiết RENTER
    @GetMapping("/renters/{userId}/detail")
    public ResponseEntity<RenterDetailResponseDTO> getRenterDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(employeeService.getRenterDetail(userId));
    }

    // Chi tiết OWNER
    @GetMapping("/owners/{userId}/detail")
    public ResponseEntity<OwnerDetailResponseDTO> getOwnerDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(employeeService.getOwnerDetail(userId));
    }

    // AI SUBSCRIPTION TIER
    @GetMapping("/ai-tiers")
    public ResponseEntity<List<AiTierDTO>> getAllAiTiers() {
        return ResponseEntity.ok(employeeService.getAllAiTiers());
    }

    @PostMapping("/ai-tiers")
    public ResponseEntity<AiTierDTO> createAiTier(@RequestBody AiTierDTO dto) {
        return ResponseEntity.ok(employeeService.createAiTier(dto));
    }

    @PutMapping("/ai-tiers/{id}")
    public ResponseEntity<AiTierDTO> updateAiTier(@PathVariable Long id, @RequestBody AiTierDTO dto) {
        return ResponseEntity.ok(employeeService.updateAiTier(id, dto));
    }

    @DeleteMapping("/ai-tiers/{id}")
    public ResponseEntity<String> deleteAiTier(@PathVariable Long id) {
        employeeService.deleteAiTier(id);
        return ResponseEntity.ok("Xóa gói AI thành công!");
    }

    // SPONSOR TIER
    @GetMapping("/sponsor-tiers")
    public ResponseEntity<List<SponsorTierDTO>> getAllSponsorTiers() {
        return ResponseEntity.ok(employeeService.getAllSponsorTiers());
    }

    @PostMapping("/sponsor-tiers")
    public ResponseEntity<SponsorTierDTO> createSponsorTier(@RequestBody SponsorTierDTO dto) {
        return ResponseEntity.ok(employeeService.createSponsorTier(dto));
    }

    @PutMapping("/sponsor-tiers/{id}")
    public ResponseEntity<SponsorTierDTO> updateSponsorTier(@PathVariable Long id, @RequestBody SponsorTierDTO dto) {
        return ResponseEntity.ok(employeeService.updateSponsorTier(id, dto));
    }

    @DeleteMapping("/sponsor-tiers/{id}")
    public ResponseEntity<String> deleteSponsorTier(@PathVariable Long id) {
        employeeService.deleteSponsorTier(id);
        return ResponseEntity.ok("Xóa gói Tài trợ thành công!");
    }
}