package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
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

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(employeeService.getAllUsers());
    }

    @GetMapping("/warehouses/pending")
    public ResponseEntity<List<WarehouseResponseDTO>> getPendingWarehouses() {
        return ResponseEntity.ok(employeeService.getPendingWarehouses());
    }

    @PatchMapping("/warehouses/{id}/verify")
    public ResponseEntity<WarehouseResponseDTO> verifyWarehouse(
            @PathVariable Long id,
            @RequestBody WarehouseVerifyDTO dto) {
        return ResponseEntity.ok(employeeService.verifyWarehouse(id, dto.status()));
    }
}