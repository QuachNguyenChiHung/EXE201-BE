package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.entity.WarehouseView;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.WarehouseRepository;
import com.ailogis.api.repository.WarehouseViewRepository;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseViewRepository warehouseViewRepository;
    private final WarehouseMapper warehouseMapper;

    public List<WarehouseResponseDTO> getActiveOnlyWarehouses() {
        return warehouseRepository.findByStatus(WarehouseStatus.ACTIVE)
                .stream().map(warehouseMapper::toWarehouseResponseDTO).toList();
    }

    // DÀNH CHO OWNER/EMPLOYEE: Chỉ xem dữ liệu chi tiết thô, KHÔNG ghi nhận log lượt xem mới
    public WarehouseResponseDTO getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));
        return warehouseMapper.toWarehouseResponseDTO(warehouse);
    }

    // DÀNH CHO PUBLIC/RENTER: Xem chi tiết + Ghi nhận log lượt xem
    @Transactional
    public WarehouseResponseDTO getWarehouseDetailWithViewTracking(Long id, CustomUserDetails userDetails) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        // Chỉ tạo log view nếu là Renter (người dùng đăng nhập)
        if (userDetails != null && userDetails.getUser().getRole() == Role.RENTER) {
            WarehouseView viewLog = WarehouseView.builder()
                    .renter(userDetails.getUser())
                    .warehouse(warehouse)
                    .viewDate(LocalDate.now())
                    .build();
            warehouseViewRepository.save(viewLog);
        }
        return warehouseMapper.toWarehouseResponseDTO(warehouse);
    }

    public Map<String, Object> getWarehouseViewStats(Long warehouseId, int days, CustomUserDetails userDetails) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        Role role = userDetails.getUser().getRole();
        if (role != Role.EMPLOYEE && !warehouse.getOwner().getId().equals(userDetails.getUser().getId())) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền xem thống kê của kho bãi này!");
        }

        if (days <= 0) days = 7;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> rawStats = warehouseViewRepository.countViewsByDateForWarehouse(warehouseId, startDate);

        Map<String, Long> statsMap = new HashMap<>();
        long totalViews = 0;
        for (Object[] row : rawStats) {
            String dateStr = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            statsMap.put(dateStr, count);
            totalViews += count;
        }

        List<String> dates = new ArrayList<>();
        List<Long> viewTrend = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dateStr = date.toString();
            dates.add(dateStr);
            viewTrend.add(statsMap.getOrDefault(dateStr, 0L));
        }

        return Map.of(
                "warehouseId", warehouseId,
                "warehouseName", warehouse.getName(),
                "days", days,
                "totalViewsInPeriod", totalViews,
                "dates", dates,
                "viewTrend", viewTrend
        );
    }
}