package com.ailogis.api.mapper;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.ContractRepository;
import com.ailogis.api.repository.RentalRequestRepository;
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
    private final ContractRepository contractRepository;
    private final RentalRequestRepository rentalRequestRepository;

    public WarehouseResponseDTO toWarehouseResponseDTO(Warehouse w) {
        List<WarehouseSectionDTO> sectionDTOs = w.getSections() != null ? w.getSections().stream().map(s -> {
            Double rentedArea = null;
            if (s.getId() != null) {
                rentedArea = contractRepository.sumActiveRentedAreaBySection(s.getId());
            }
            double activeRented = rentedArea != null ? rentedArea : 0.0;
            double totalCap = s.getTotalCapacity() != null ? s.getTotalCapacity() : 0.0;
            double realAvailable = Math.max(0.0, totalCap - activeRented);

            return new WarehouseSectionDTO(
                    s.getId(),
                    s.getSector(),
                    totalCap,
                    realAvailable,
                    s.getTempMin(),
                    s.getTempMax(),
                    s.getHumidity(),
                    s.getHasCertification(),
                    s.getPriceTiers() != null ? s.getPriceTiers().stream()
                            .filter(p -> p.getIsActive() == null || p.getIsActive())
                            .map(p -> new PriceTierDTO(p.getId(), p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList() : List.of()
            );
        }).toList() : List.of();

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

        // 4. Lấy thống kê lượt xem
        Map<String, Long> viewStats = new HashMap<>();
        if (w.getId() != null) {
            List<Object[]> rawStats = warehouseViewRepository.countViewsByDateForWarehouse(w.getId());
            for (Object[] row : rawStats) {
                viewStats.put((String) row[0], (Long) row[1]);
            }
        }

        // 5. Đếm Request đang chờ duyệt
        long pendingReq = 0L;
        if (w.getId() != null) {
            pendingReq = rentalRequestRepository.countByWarehouseIdAndStatus(w.getId(), RequestStatus.PENDING);
        }

        String displayStatus = calculateOperationalStatus(w);

        SponsorTierDTO sponsorDto = null;
        if (w.getIsSponsor() != null && w.getIsSponsor() && w.getSponsorType() != null) {
            sponsorDto = new SponsorTierDTO(
                    w.getSponsorType().getId(),
                    w.getSponsorType().getPriorityLevel(),
                    w.getSponsorType().getPricingPerMonth(),
                    w.getSponsorType().getYearPackSale(),
                    w.getSponsorType().getLabel(),
                    null,
                    w.getSponsorType().getIsActive()
            );
        }

        return new WarehouseResponseDTO(
                w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(),
                w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, displayStatus, viewStats, pendingReq,
                w.getIsSponsor(), sponsorDto
        );
    }

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