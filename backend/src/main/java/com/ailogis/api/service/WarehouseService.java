package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public List<WarehouseResponseDTO> getActiveOnlyWarehouses() {
        return warehouseRepository.findByStatus(WarehouseStatus.ACTIVE)
                .stream().map(this::mapToResponseDTO).toList();
    }

    public WarehouseResponseDTO getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));
        return mapToResponseDTO(warehouse);
    }

    private WarehouseResponseDTO mapToResponseDTO(Warehouse w) {
        // 1. Map danh sách các phòng
        List<WarehouseSectionDTO> sectionDTOs = w.getSections().stream().map(s ->
                new WarehouseSectionDTO(
                        s.getSector(), s.getTotalCapacity(), s.getTempMin(), s.getTempMax(), s.getHumidity(), s.getHasCertification(),
                        s.getPriceTiers().stream().map(p -> new PriceTierDTO(p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList()
                )
        ).toList();

        // 2. Map danh sách ảnh (có check null an toàn)
        List<WarehouseImageDTO> imageDTOs = w.getImages() != null ?
                w.getImages().stream().map(i -> new WarehouseImageDTO(i.getId(), i.getImageUrl(), i.getIsThumbnail())).toList() : List.of();

        // 3. BỔ SUNG: Map danh sách chứng chỉ PDF (để Renter có thể xem chứng chỉ)
        List<CertificationSubmitDTO> certDTOs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(c.getId(), c.getType().getLabel(), c.getLink(), c.getIsVerified())
                ).toList() : List.of();

        String displayStatus = calculateOperationalStatus(w);

        // 4. Trả về DTO (Đã fix lỗi dấu chấm phẩy và thêm certDTOs)
        return new WarehouseResponseDTO(
                w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(),
                w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs,
                displayStatus
        );
    }

    private String calculateOperationalStatus(Warehouse w) {
        // Nếu kho bị từ chối hoặc admin set Inactive
        if (w.getStatus() == WarehouseStatus.REJECTED || w.getStatus() == WarehouseStatus.INACTIVE) {
            return "INACTIVE";
        }

        // Kiểm tra sức chứa: Nếu tổng sức chứa khả dụng = 0 -> RENTED
        boolean isFull = w.getSections().stream()
                .allMatch(s -> s.getAvailableCapacity() <= 0);

        if (isFull) return "RENTED";

        // Nếu kho đã được duyệt và còn chỗ -> ACTIVE
        if (w.getStatus() == WarehouseStatus.ACTIVE) { // Hoặc logic duyệt của bạn
            return "ACTIVE";
        }

        return "PENDING";
    }
}