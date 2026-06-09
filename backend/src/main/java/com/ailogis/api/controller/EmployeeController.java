package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(employeeService.getAllUsers());
    }

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getAllWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(null));
    }

    @GetMapping("/warehouses/pending")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getPendingWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(WarehouseStatus.PENDING));
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
}