package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.entity.WarehouseView;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.CertificationTypeRepository;
import com.ailogis.api.repository.SponsorTierRepository;
import com.ailogis.api.repository.WarehouseRepository;
import com.ailogis.api.repository.WarehouseViewRepository;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final SponsorTierRepository sponsorTierRepository;
    private final CertificationTypeRepository certificationTypeRepository;

    public Page<WarehouseResponseDTO> getActiveOnlyWarehouses(Pageable pageable) {
        return warehouseRepository.findByStatus(WarehouseStatus.ACTIVE, pageable)
                .map(warehouseMapper::toWarehouseResponseDTO);
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

    public WarehouseLocationDTO getWarehouseLocation(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        return new WarehouseLocationDTO(
                warehouse.getLocationLong(),
                warehouse.getLocationLat()
        );
    }

    public Page<WarehouseResponseDTO> getPopularWarehouses(Pageable pageable) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return warehouseRepository.findPopularWarehouses(thirtyDaysAgo, pageable)
                .map(warehouseMapper::toWarehouseResponseDTO);
    }

    public Page<WarehouseResponseDTO> searchWarehouses(
            String province, Boolean isSponsor, Double minArea, Double maxArea,
            Double minPrice, Double maxPrice, Double minRating, Pageable pageable) {

        return warehouseRepository.searchWarehouses(province, isSponsor, minArea, maxArea, minPrice, maxPrice, minRating, pageable)
                .map(warehouseMapper::toWarehouseResponseDTO);
    }

    public FilterMetaResponseDTO getFilterMeta() {
        // 1. Lấy danh sách Địa điểm thực tế đang có kho bãi
        List<String> locations = warehouseRepository.findDistinctProvinces();

        // 2. Trạng thái mà khách thuê được phép tìm kiếm
        List<String> statuses = List.of(
                WarehouseStatus.ACTIVE.name(),
                WarehouseStatus.RENTED.name()
        );

        // 3. Danh sách các gói Sponsor để Frontend vẽ nút
        List<SponsorTierDTO> sponsorTiers = sponsorTierRepository.findByIsActiveTrue().stream()
                .map(t -> new SponsorTierDTO(t.getId(), t.getPriorityLevel(), t.getPricingPerMonth(), t.getYearPackSale(), t.getLabel(), null, t.getIsActive()))
                .toList();

        // 4. Danh sách các Chứng chỉ (HACCP, ISO...)
        List<CertDTO> certifications = certificationTypeRepository.findAll().stream()
                .map(c -> new CertDTO(c.getId().toString(), c.getLabel(), c.getLawReferences(), c.getUpdateDate() != null ? c.getUpdateDate().toString() : null, c.getPdfLink()))
                .toList();

        return new FilterMetaResponseDTO(locations, statuses, sponsorTiers, certifications);
    }
}