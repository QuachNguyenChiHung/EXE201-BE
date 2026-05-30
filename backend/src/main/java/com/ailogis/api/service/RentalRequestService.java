package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RentalRequestService {

    private final RentalRequestRepository requestRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final WarehouseSectionRepository sectionRepository;
    private final PriceTierRepository priceTierRepository;

    @Transactional
    public RentRequestResponseDTO createRequest(Long renterId, RentRequestCreateDTO dto) {
        User renter = userRepository.findById(renterId).orElseThrow(() -> new RuntimeException("Renter không tồn tại"));
        Warehouse warehouse = warehouseRepository.findById(dto.warehouseId()).orElseThrow(() -> new RuntimeException("Kho không tồn tại"));

        RentalRequest request = RentalRequest.builder()
                .renter(renter).warehouse(warehouse).cargoDescription(dto.cargoDescription())
                .otherDetail(dto.otherDetail()).duration(dto.duration()).durationUnit(dto.durationUnit())
                .status(RequestStatus.PENDING).build();

        List<RentRequestDetail> details = dto.details().stream().map(dDto -> {
            WarehouseSection section = sectionRepository.findById(dDto.sectionId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng kho"));
            PriceTier priceTier = priceTierRepository.findById(dDto.priceTierId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy biểu giá"));

            // Kiểm tra sức chứa trống của riêng CĂN PHÒNG đó
            if (dDto.rentedArea() > section.getAvailableCapacity()) {
                throw new RuntimeException("Phòng số " + section.getSector() + " không đủ diện tích trống!");
            }

            return RentRequestDetail.builder()
                    .rentRequest(request).section(section).priceTier(priceTier)
                    .rentedArea(dDto.rentedArea()).areaUnit(dDto.areaUnit()).build();
        }).toList();

        request.setDetails(details);
        return mapToResponseDTO(requestRepository.save(request));
    }

    public List<RentRequestResponseDTO> getRequestsByRenter(Long renterId) {
        return requestRepository.findByRenterId(renterId).stream().map(this::mapToResponseDTO).toList();
    }

    private RentRequestResponseDTO mapToResponseDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit())
        ).toList();

        return new RentRequestResponseDTO(r.getId(), r.getWarehouse().getName(), r.getCargoDescription(), r.getDuration(), r.getDurationUnit(), r.getStatus().name(), detailDTOs);
    }
}