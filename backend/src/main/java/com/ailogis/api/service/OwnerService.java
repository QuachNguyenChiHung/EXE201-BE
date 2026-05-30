package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.RentalRequest;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.repository.CertificationTypeRepository;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.repository.UserRepository;
import com.ailogis.api.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RentalRequestRepository requestRepository;
    private final CertificationTypeRepository certificationTypeRepository;

    // 1. Xem danh sách kho của mình
    public List<WarehouseResponseDTO> getMyWarehouses(Long ownerId) {
        return warehouseRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToWarehouseDTO).toList();
    }

    // 2. Xem các đơn yêu cầu thuê kho do mình sở hữu
    public List<RentRequestResponseDTO> getRequestsForMyWarehouses(Long ownerId) {
        return requestRepository.findAll().stream()
                // Lọc ra những Request thuộc về Kho của Owner này
                .filter(req -> req.getWarehouse().getOwner().getId().equals(ownerId))
                .map(this::mapToRequestDTO)
                .toList();
    }

    // 3. Duyệt hoặc Từ chối yêu cầu thuê
    @Transactional
    public RentRequestResponseDTO updateRequestStatus(Long ownerId, Long requestId, RequestStatus newStatus) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê này"));

        if (!request.getWarehouse().getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên yêu cầu của kho này!");
        }

        request.setStatus(newStatus);
        RentalRequest updatedRequest = requestRepository.save(request);
        return mapToRequestDTO(updatedRequest);
    }

    // --- Helpers Map Data ---
    private WarehouseResponseDTO mapToWarehouseDTO(com.ailogis.api.entity.Warehouse w) {
        // Map sections
        java.util.List<WarehouseSectionDTO> sectionDTOs = w.getSections().stream().map(s ->
                new WarehouseSectionDTO(s.getSector(), s.getTotalCapacity(), s.getTempMin(), s.getTempMax(), s.getHumidity(), s.getHasCertification(),
                        s.getPriceTiers().stream().map(p -> new PriceTierDTO(p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList()
                )).toList();

        // Map images an toàn (check null)
        java.util.List<WarehouseImageDTO> imageDTOs = w.getImages() != null ?
                w.getImages().stream().map(i -> new WarehouseImageDTO(i.getId(), i.getImageUrl(), i.getIsThumbnail())).toList() : java.util.List.of();

        // Map certificates (BỔ SUNG)
        java.util.List<CertificationSubmitDTO> certDTOs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(c.getId(), c.getType().getLabel(), c.getLink(), c.getIsVerified())
                ).toList() : java.util.List.of();

        // Cập nhật lại DTO có thêm tham số certDTOs
        return new WarehouseResponseDTO(w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(), w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, w.getStatus().name());
    }

    private RentRequestResponseDTO mapToRequestDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit())
        ).toList();
        return new RentRequestResponseDTO(r.getId(), r.getWarehouse().getName(), r.getCargoDescription(), r.getDuration(), r.getDurationUnit(), r.getStatus().name(), detailDTOs);
    }

    @Transactional
    public WarehouseResponseDTO createWarehouse(Long ownerId, WarehouseCreateDTO dto, java.util.List<String> imageUrls, String certificateUrl) {
        com.ailogis.api.entity.User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Chủ kho không tồn tại!"));

        // 1. Khởi tạo đối tượng Kho (Warehouse)
        com.ailogis.api.entity.Warehouse warehouse = com.ailogis.api.entity.Warehouse.builder()
                .owner(owner)
                .name(dto.name())
                .description(dto.description())
                .locationAddressText(dto.locationAddressText())
                .locationProvince(dto.locationProvince())
                .locationCommune(dto.locationCommune())
                .status(com.ailogis.api.enums.WarehouseStatus.PENDING)
                .isSponsor(false)
                .build();

        // 2. Xử lý logic lồng nhau: Map Sections và PriceTiers
        if (dto.sections() != null) {
            java.util.List<com.ailogis.api.entity.WarehouseSection> sections = dto.sections().stream().map(secDto -> {
                com.ailogis.api.entity.WarehouseSection section = com.ailogis.api.entity.WarehouseSection.builder()
                        .warehouse(warehouse)
                        .sector(secDto.sector())
                        .totalCapacity(secDto.totalCapacity())
                        .availableCapacity(secDto.totalCapacity())
                        .tempMin(secDto.tempMin())
                        .tempMax(secDto.tempMax())
                        .humidity(secDto.humidity())
                        .hasCertification(secDto.hasCertification())
                        .build();

                if (secDto.priceTiers() != null) {
                    java.util.List<com.ailogis.api.entity.PriceTier> priceTiers = secDto.priceTiers().stream().map(ptDto ->
                            com.ailogis.api.entity.PriceTier.builder()
                                    .section(section)
                                    .label(ptDto.label())
                                    .value(ptDto.value())
                                    .unit(ptDto.unit())
                                    .areaUnit(ptDto.areaUnit())
                                    .build()
                    ).toList();
                    section.setPriceTiers(priceTiers);
                }
                return section;
            }).toList();
            warehouse.setSections(sections);
        }

        // 3. Xử lý mảng Hình ảnh (WarehouseImage)
        if (imageUrls != null && !imageUrls.isEmpty()) {
            java.util.List<com.ailogis.api.entity.WarehouseImage> images = new java.util.ArrayList<>();
            for (int i = 0; i < imageUrls.size(); i++) {
                images.add(com.ailogis.api.entity.WarehouseImage.builder()
                        .warehouse(warehouse)
                        .imageUrl(imageUrls.get(i))
                        .isThumbnail(i == 0)
                        .displayOrder(i)
                        .build());
            }
            warehouse.setImages(images);
        }

        // 4. XỬ LÝ LƯU PDF CHỨNG CHỈ (SỬA LẠI ĐOẠN NÀY)
        if (certificateUrl != null) {
            com.ailogis.api.entity.CertificationType type = certificationTypeRepository.findById(1L)
                    .orElseGet(() -> {
                        com.ailogis.api.entity.CertificationType newType = com.ailogis.api.entity.CertificationType.builder()
                                .label("Chứng nhận Cơ bản").build();
                        return certificationTypeRepository.save(newType);
                    });

            com.ailogis.api.entity.CertificationSubmit certSubmit = com.ailogis.api.entity.CertificationSubmit.builder()
                    .warehouse(warehouse)
                    .type(type)
                    .link(certificateUrl)
                    .isVerified(false)
                    .build();

            warehouse.getCertificationSubmits().add(certSubmit);
        }

        // 5. Lưu 1 lần xuống 5 bảng cùng lúc!
        com.ailogis.api.entity.Warehouse savedWarehouse = warehouseRepository.save(warehouse);

        return mapToWarehouseDTO(savedWarehouse);
    }
}