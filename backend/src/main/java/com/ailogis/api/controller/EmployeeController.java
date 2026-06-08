package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getAllWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(null));
    }

    @GetMapping("/warehouses/pending")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getPendingWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(WarehouseStatus.PENDING));
    }

    @GetMapping("/warehouses/accepted")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getAcceptedWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(WarehouseStatus.APPROVED));
    }

    @GetMapping("/warehouses/hidden")
    public ResponseEntity<List<WarehouseEmployeeDTO>> getHiddenWarehouses() {
        return ResponseEntity.ok(employeeService.getWarehousesByStatus(WarehouseStatus.HIDDEN));
    }

    @PatchMapping("/warehouses/{id}/accept")
    public ResponseEntity<WarehouseEmployeeDTO> acceptWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.changeWarehouseStatus(id, WarehouseStatus.APPROVED));
    }

    @PatchMapping("/warehouses/{id}/rejected")
    public ResponseEntity<WarehouseEmployeeDTO> rejectWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.changeWarehouseStatus(id, WarehouseStatus.REJECTED));
    }

    @PatchMapping("/warehouses/{id}/hidden")
    public ResponseEntity<WarehouseEmployeeDTO> hideWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.changeWarehouseStatus(id, WarehouseStatus.HIDDEN));
    }

    @GetMapping("/statistic")
    public ResponseEntity<StatisticResponseDTO> getGlobalStatistics() {
        return ResponseEntity.ok(employeeService.getGlobalStatistics());
    }

    @GetMapping("/users-statistic")
    public ResponseEntity<UserStatisticResponseDTO> getUsersStatistics() {
        return ResponseEntity.ok(employeeService.getUsersStatistics());
    }
}