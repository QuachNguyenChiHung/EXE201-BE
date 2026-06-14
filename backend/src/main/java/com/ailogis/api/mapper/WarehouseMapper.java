package com.ailogis.api.mapper;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.WarehouseViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WarehouseMapper {

    private final WarehouseViewRepository warehouseViewRepository;

    public WarehouseResponseDTO toWarehouseResponseDTO(Warehouse w) {
        // 1. Map Sections
        List<WarehouseSectionDTO> sectionDTOs = w.getSections() != null ? w.getSections().stream().map(s ->
                new WarehouseSectionDTO(
                        s.getSector(), s.getTotalCapacity(), s.getTempMin(), s.getTempMax(), s.getHumidity(), s.getHasCertification(),
                        s.getPriceTiers() != null ? s.getPriceTiers().stream().map(p -> new PriceTierDTO(p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList() : List.of()
                )
        ).toList() : List.of();

        // 2. Map Images
        List<WarehouseImageDTO> imageDTOs = w.getImages() != null ?
                w.getImages().stream().map(i -> new WarehouseImageDTO(i.getId(), i.getImageUrl(), i.getIsThumbnail())).toList() : List.of();

        // 3. Map Certifications
        List<CertificationSubmitDTO> certDTOs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(
                                c.getId(),
                                c.getType() != null ? c.getType().getLabel() : "Chưa phân loại",
                                c.getLink(),
                                c.getStatus() != null ? c.getStatus().name() : "PENDING",
                                c.getRejectReason()
                        )
                ).toList() : List.of();

        // 4. Lấy thống kê lượt xem từ Repository
        Map<String, Long> viewStats = new HashMap<>();
        if (w.getId() != null) {
            List<Object[]> rawStats = warehouseViewRepository.countViewsByDateForWarehouse(w.getId());
            for (Object[] row : rawStats) {
                viewStats.put((String) row[0], (Long) row[1]);
            }
        }

        // 5. Tính toán trạng thái hiển thị
        String displayStatus = calculateOperationalStatus(w);

        // Trả về DTO hoàn chỉnh có đủ viewCountByDate
        return new WarehouseResponseDTO(
                w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(),
                w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, displayStatus, viewStats
        );
    }

    // Helper tính trạng thái
    private String calculateOperationalStatus(Warehouse w) {
        if (w.getStatus() == WarehouseStatus.REJECTED || w.getStatus() == WarehouseStatus.INACTIVE) {
            return "INACTIVE";
        }
        boolean isFull = w.getSections() != null && !w.getSections().isEmpty() &&
                w.getSections().stream().allMatch(s -> s.getAvailableCapacity() != null && s.getAvailableCapacity() <= 0);

        if (isFull && w.getStatus() == WarehouseStatus.ACTIVE) return "RENTED";
        return w.getStatus().name();
    }
}