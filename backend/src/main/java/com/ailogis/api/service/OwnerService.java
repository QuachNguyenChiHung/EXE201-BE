package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RentalRequestRepository requestRepository;
    private final CertificationTypeRepository certificationTypeRepository;

    public List<WarehouseResponseDTO> getMyWarehouses(Long ownerId) {
        return warehouseRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToWarehouseDTO).toList();
    }

    public List<RentRequestResponseDTO> getRequestsForMyWarehouses(Long ownerId) {
        return requestRepository.findAll().stream()
                .filter(req -> req.getWarehouse().getOwner().getId().equals(ownerId))
                .map(this::mapToRequestDTO)
                .toList();
    }

    @Transactional
    public RentRequestResponseDTO updateRequestStatus(Long ownerId, Long requestId, RequestStatus newStatus) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê này"));

        if (!request.getWarehouse().getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên yêu cầu của kho này!");
        }

        request.setStatus(newStatus);
        return mapToRequestDTO(requestRepository.save(request));
    }

    private WarehouseResponseDTO mapToWarehouseDTO(Warehouse w) {
        List<WarehouseSectionDTO> sectionDTOs = w.getSections().stream().map(s ->
                new WarehouseSectionDTO(s.getSector(), s.getTotalCapacity(), s.getTempMin(), s.getTempMax(), s.getHumidity(), s.getHasCertification(),
                        s.getPriceTiers().stream().map(p -> new PriceTierDTO(p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList()
                )).toList();

        List<WarehouseImageDTO> imageDTOs = w.getImages() != null ?
                w.getImages().stream().map(i -> new WarehouseImageDTO(i.getId(), i.getImageUrl(), i.getIsThumbnail())).toList() : List.of();

        List<CertificationSubmitDTO> certDTOs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(c.getId(), c.getType().getLabel(), c.getLink(), c.getIsVerified())
                ).toList() : List.of();

        return new WarehouseResponseDTO(w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(), w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, w.getStatus().name());
    }

    private RentRequestResponseDTO mapToRequestDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit())
        ).toList();
        return new RentRequestResponseDTO(r.getId(), r.getWarehouse().getName(), r.getCargoDescription(), r.getDuration(), r.getDurationUnit(), r.getStatus().name(), detailDTOs);
    }

    @Transactional
    public WarehouseResponseDTO createWarehouse(Long ownerId, WarehouseCreateDTO dto, List<String> imageUrls, String certificateUrl) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Chủ kho không tồn tại!"));

        // Khởi tạo kho
        Warehouse warehouse = Warehouse.builder()
                .owner(owner)
                .name(dto.name())
                .description(dto.description())
                .locationAddressText(dto.locationAddressText())
                .locationProvince(dto.locationProvince())
                .locationCommune(dto.locationCommune())
                .status(WarehouseStatus.PENDING)
                .isSponsor(false)
                .sections(new ArrayList<>())
                .images(new ArrayList<>())
                .certificationSubmits(new ArrayList<>())
                .build();

        // Map Sections & PriceTiers
        if (dto.sections() != null) {
            List<WarehouseSection> sections = dto.sections().stream().map(secDto -> {
                WarehouseSection section = WarehouseSection.builder()
                        .warehouse(warehouse)
                        .sector(secDto.sector())
                        .totalCapacity(secDto.totalCapacity())
                        .availableCapacity(secDto.totalCapacity())
                        .tempMin(secDto.tempMin())
                        .tempMax(secDto.tempMax())
                        .humidity(secDto.humidity())
                        .hasCertification(secDto.hasCertification())
                        .priceTiers(new ArrayList<>())
                        .build();

                if (secDto.priceTiers() != null) {
                    List<PriceTier> priceTiers = secDto.priceTiers().stream().map(ptDto ->
                            PriceTier.builder()
                                    .section(section)
                                    .label(ptDto.label())
                                    .value(ptDto.value())
                                    .unit(ptDto.unit())
                                    .areaUnit(ptDto.areaUnit())
                                    .build()
                    ).toList();
                    section.getPriceTiers().addAll(priceTiers);
                }
                return section;
            }).toList();
            warehouse.getSections().addAll(sections);
        }

        // Map Images
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                warehouse.getImages().add(WarehouseImage.builder()
                        .warehouse(warehouse)
                        .imageUrl(imageUrls.get(i))
                        .isThumbnail(i == 0)
                        .displayOrder(i)
                        .build());
            }
        }

        // Map Certificate
        if (certificateUrl != null) {
            CertificationType type = certificationTypeRepository.findById(1L)
                    .orElseGet(() -> certificationTypeRepository.save(CertificationType.builder().label("Chứng nhận Cơ bản").build()));

            warehouse.getCertificationSubmits().add(CertificationSubmit.builder()
                    .warehouse(warehouse)
                    .type(type)
                    .link(certificateUrl)
                    .isVerified(false)
                    .build());
        }

        return mapToWarehouseDTO(warehouseRepository.save(warehouse));
    }
}