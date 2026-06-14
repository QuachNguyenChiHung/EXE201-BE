package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.ContractStatus;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.VerifyStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RentalRequestRepository requestRepository;
    private final CertificationTypeRepository certificationTypeRepository;
    private final WarehouseMapper warehouseMapper;
    private final TransactionRepository transactionRepository;
    private final ContractRepository contractRepository;

    public List<WarehouseResponseDTO> getMyWarehouses(Long ownerId) {
        return warehouseRepository.findByOwnerId(ownerId).stream()
                .map(warehouseMapper::toWarehouseResponseDTO)
                .toList();
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

    private RentRequestResponseDTO mapToRequestDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit())
        ).toList();
        return new RentRequestResponseDTO(
                r.getId(),
                r.getWarehouse().getName(),
                r.getCargoDescription(),
                r.getDuration(),
                r.getDurationUnit(),
                r.getStatus().name(),
                r.getOtherDetail(),
                r.getRenterRejectionReason(),
                r.getRejectionReason(),
                r.getOfferedPrice(),
                r.getOwnerNote(),
                detailDTOs
        );
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
                    .status(VerifyStatus.PENDING)
                    .build());
        }

        return warehouseMapper.toWarehouseResponseDTO(warehouseRepository.save(warehouse));
    }

    public OwnerStatisticResponseDTO getOwnerStatistics(Long ownerId) {
        // 1. Thống kê Kho bãi & Sức chứa
        List<Warehouse> warehouses = warehouseRepository.findByOwnerId(ownerId);
        long totalWarehouses = warehouses.size();

        double totalCapacity = 0.0;
        double totalAvailable = 0.0;

        for (Warehouse w : warehouses) {
            for (WarehouseSection s : w.getSections()) {
                totalCapacity += s.getTotalCapacity() != null ? s.getTotalCapacity() : 0;
                totalAvailable += s.getAvailableCapacity() != null ? s.getAvailableCapacity() : 0;
            }
        }

        // Tỷ lệ lấp đầy = (Tổng chứa - Trống) / Tổng chứa * 100
        double occupancyRate = 0.0;
        if (totalCapacity > 0) {
            occupancyRate = ((totalCapacity - totalAvailable) / totalCapacity) * 100.0;
        }

        // 2. Thống kê Request (Chỉ đếm các Request Pending tạo/cập nhật trong 30 ngày gần đây)
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long pendingRequests = requestRepository.countPendingRequestsByOwner(ownerId, thirtyDaysAgo);

        // 3. Thống kê Contract
        long activeContracts = contractRepository.countByOwnerIdAndStatus(
                ownerId,
                ContractStatus.ACTIVE
        );

        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        long endingContracts = contractRepository.countEndingContracts(ownerId, thirtyDaysFromNow);

        // 4. Thống kê Billing (Tổng tiền mua Sponsor trong tháng hiện tại)
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        Double billing = transactionRepository.sumSponsorBillingByDateRange(
                ownerId,
                startOfMonth,
                endOfMonth
        );

        return new OwnerStatisticResponseDTO(
                totalWarehouses,
                totalCapacity,
                totalAvailable,
                Math.round(occupancyRate * 100.0) / 100.0,
                pendingRequests,
                activeContracts,
                billing != null ? billing : 0.0,
                endingContracts
        );
    }
}