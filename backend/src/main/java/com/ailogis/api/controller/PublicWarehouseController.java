package com.ailogis.api.controller;

import com.ailogis.api.dto.WarehouseResponseDTO;
import com.ailogis.api.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PublicWarehouseController {
    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<List<WarehouseResponseDTO>> getAll() {
        return ResponseEntity.ok(warehouseService.getAllApprovedWarehouses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponseDTO> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }
}