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

    public List<WarehouseResponseDTO> getAllApprovedWarehouses() {
        return warehouseRepository.findByStatus(WarehouseStatus.APPROVED)
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

        // 4. Trả về DTO (Đã fix lỗi dấu chấm phẩy và thêm certDTOs)
        return new WarehouseResponseDTO(
                w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(),
                w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, w.getStatus().name()
        );
    }
}