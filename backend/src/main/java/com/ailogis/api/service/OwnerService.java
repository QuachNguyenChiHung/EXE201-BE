package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.ContractStatus;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.VerifyStatus;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.mapper.ContractMapper;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ReviewRepository reviewRepository;
    private final SponsorTierRepository sponsorTierRepository;
    private final PaymentService paymentService;
    private final ContractMapper contractMapper;
    private final FileStorageService fileStorageService;

    public Page<WarehouseResponseDTO> getMyWarehouses(Long ownerId, String statusStr, Pageable pageable) {
        WarehouseStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = WarehouseStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái kho bãi không hợp lệ!");
            }
        }

        return warehouseRepository.findByOwnerIdWithFilterPaged(ownerId, statusEnum, pageable)
                .map(warehouseMapper::toWarehouseResponseDTO);
    }

    public Page<RentRequestResponseDTO> getRequestsForMyWarehouses(Long ownerId, String statusStr, Pageable pageable) {
        RequestStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = RequestStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái Request không hợp lệ!");
            }
        }
        return requestRepository.findByWarehouseOwnerIdWithFilter(ownerId, statusEnum, pageable).map(this::mapToRequestDTO);
    }

    @Transactional
    public RentRequestResponseDTO updateRequestStatus(Long ownerId, Long requestId, RequestStatusUpdateDTO dto) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê này"));

        if (!request.getWarehouse().getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên yêu cầu của kho này!");
        }

        // Cập nhật trạng thái
        if (dto.status() != null) {
            request.setStatus(dto.status());
        }

        // Cập nhật lý do từ chối
        if (dto.status() == RequestStatus.REJECTED && dto.rejectionReason() != null) {
            request.setRejectionReason(dto.rejectionReason());
        }

        // Cập nhật giá thương lượng
        if (dto.offeredPrice() != null) {
            request.setOfferedPrice(dto.offeredPrice());
        }

        // Lời nhắn của Owner
        if (dto.ownerNote() != null) {
            request.setOwnerNote(dto.ownerNote());
        }

        return mapToRequestDTO(requestRepository.save(request));
    }

    private RentRequestResponseDTO mapToRequestDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit())
        ).toList();
        return new RentRequestResponseDTO(
                r.getId(),
                r.getWarehouse().getName(),
                r.getRenter() != null ? r.getRenter().getFullName() : "N/A",
                r.getWarehouse().getOwner() != null ? r.getWarehouse().getOwner().getFullName() : "N/A",
                r.getCargoDescription(),
                r.getDuration(),
                r.getDurationUnit(),
                r.getStartDate(),
                r.getEndDate(),
                r.getStatus().name(),
                r.getOtherDetail(),
                r.getRenterRejectionReason(),
                r.getRejectionReason(),
                r.getOfferedPrice(),
                r.getRenterOfferedPrice(),
                r.getOwnerNote(),
                detailDTOs
        );
    }

    @Transactional
    public WarehouseResponseDTO createWarehouse(Long ownerId, WarehouseCreateDTO dto, List<String> imageUrls, List<WarehouseCertCreateDTO> certificates) {
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
                .locationLong(dto.locationLong())
                .locationLat(dto.locationLat())
                .locationPostalCode(dto.locationPostalCode())
                .status(WarehouseStatus.PENDING)
                .isSponsor(false)
                .sections(new java.util.ArrayList<>())
                .images(new java.util.ArrayList<>())
                .certificationSubmits(new java.util.ArrayList<>())
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
        if (certificates != null && !certificates.isEmpty()) {
            for (WarehouseCertCreateDTO certDto : certificates) {
                CertificationType type = certificationTypeRepository.findById(certDto.certTypeId())
                        .orElseThrow(() -> new RuntimeException("Loại chứng chỉ với ID " + certDto.certTypeId() + " không tồn tại!"));

                warehouse.getCertificationSubmits().add(CertificationSubmit.builder()
                        .warehouse(warehouse)
                        .type(type)
                        .link(certDto.link())
                        .status(VerifyStatus.PENDING) // Chứng chỉ mới nộp luôn phải chờ Admin duyệt
                        .build());
            }
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
                double cap = s.getTotalCapacity() != null ? s.getTotalCapacity() : 0.0;
                totalCapacity += cap;

                Double activeRented = contractRepository.sumActiveRentedAreaBySection(s.getId());
                double rented = activeRented != null ? activeRented : 0.0;
                totalAvailable += Math.max(0.0, cap - rented);
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
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).atTime(23, 59, 59);

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

    public WarehouseRatingResponseDTO getWarehouseRatings(Long ownerId, Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền xem thống kê đánh giá của kho này!");
        }

        List<Review> reviews = reviewRepository.findByWarehouseId(warehouseId);

        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);

        List<ReviewResponseDTO> dtos = reviews.stream().map(r -> new ReviewResponseDTO(
                r.getId(),
                r.getUser() != null ? r.getUser().getFullName() : "Khách hàng ẩn danh",
                r.getRating(),
                r.getComment()
        )).toList();

        return new WarehouseRatingResponseDTO(
                Math.round(avg * 10.0) / 10.0,
                reviews.size(),
                dtos
        );
    }

    @Transactional
    public WarehouseResponseDTO deleteWarehouse(Long ownerId, Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thao tác trên kho bãi này!");
        }

        if (warehouse.getStatus() == WarehouseStatus.INACTIVE) {
            throw new RuntimeException("Kho bãi này đã ở trạng thái ngừng hoạt động từ trước!");
        }

        warehouse.setStatus(WarehouseStatus.INACTIVE);

        List<RentalRequest> pendingRequests = requestRepository.findByWarehouseIdAndStatus(warehouseId, RequestStatus.PENDING);
        for (RentalRequest req : pendingRequests) {
            req.setStatus(RequestStatus.REJECTED);
            req.setRejectionReason("Hệ thống tự động hủy: Kho bãi đã ngừng hoạt động hoặc bị chủ kho gỡ bỏ.");
        }
        if (!pendingRequests.isEmpty()) {
            requestRepository.saveAll(pendingRequests);
        }

        return warehouseMapper.toWarehouseResponseDTO(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponseDTO reactivateWarehouse(Long ownerId, Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thao tác trên kho bãi này!");
        }

        if (warehouse.getStatus() != com.ailogis.api.enums.WarehouseStatus.INACTIVE) {
            throw new RuntimeException("Kho bãi này không ở trạng thái ngừng hoạt động, không thể kích hoạt lại!");
        }

        // Kiểm tra xem kho có đang bị full bởi các hợp đồng cũ không
        boolean isFull = warehouse.getSections() != null && !warehouse.getSections().isEmpty() &&
                warehouse.getSections().stream().allMatch(s -> s.getAvailableCapacity() != null && s.getAvailableCapacity() <= 0);

        // Nếu full thì chuyển sang RENTED, nếu còn trống thì ACTIVE
        warehouse.setStatus(isFull ? com.ailogis.api.enums.WarehouseStatus.RENTED : com.ailogis.api.enums.WarehouseStatus.ACTIVE);

        return warehouseMapper.toWarehouseResponseDTO(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponseDTO updateWarehouse(Long ownerId, Long warehouseId, WarehouseUpdateDTO dto, Boolean force,
                                                List<String> newImageUrls, List<Long> deletedImageIds,
                                                List<WarehouseCertCreateDTO> newCertificates, List<Long> deletedCertIds) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thao tác trên kho này!");
        }

        List<Contract> activeContracts = contractRepository.findByWarehouseIdWithFilter(warehouseId, ContractStatus.ACTIVE);
        if (!activeContracts.isEmpty() && (force == null || !force)) {
            throw new RuntimeException("Cảnh báo: Kho bãi này đang có " + activeContracts.size() + " hợp đồng vận hành. Việc thay đổi cấu trúc hoặc giá tiền có thể ảnh hưởng đến trải nghiệm của khách thuê. Vui lòng gửi lại Request kèm theo tham số ?force=true để xác nhận cập nhật.");
        }

        // 1. Cập nhật thông tin cơ bản
        if (dto.name() != null) warehouse.setName(dto.name());
        if (dto.description() != null) warehouse.setDescription(dto.description());
        if (dto.locationAddressText() != null) warehouse.setLocationAddressText(dto.locationAddressText());
        if (dto.locationProvince() != null) warehouse.setLocationProvince(dto.locationProvince());
        if (dto.locationCommune() != null) warehouse.setLocationCommune(dto.locationCommune());
        if (dto.locationLong() != null) warehouse.setLocationLong(dto.locationLong());
        if (dto.locationLat() != null) warehouse.setLocationLat(dto.locationLat());

        // 2. Cập nhật Sections và PriceTiers
        if (dto.sections() != null && !dto.sections().isEmpty()) {
            for (WarehouseSectionUpdateDTO secDto : dto.sections()) {
                WarehouseSection section;

                if (secDto.id() != null) {
                    // Trạng thái Update phòng cũ
                    section = warehouse.getSections().stream()
                            .filter(s -> s.getId().equals(secDto.id()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy phân khu với ID: " + secDto.id()));

                    if (secDto.totalCapacity() != null) section.setTotalCapacity(secDto.totalCapacity());
                    if (secDto.tempMin() != null) section.setTempMin(secDto.tempMin());
                    if (secDto.tempMax() != null) section.setTempMax(secDto.tempMax());
                    if (secDto.humidity() != null) section.setHumidity(secDto.humidity());
                    if (secDto.hasCertification() != null) section.setHasCertification(secDto.hasCertification());

                    // Price tier versioning
                    if (secDto.priceTiers() != null && !secDto.priceTiers().isEmpty()) {
                        // Ẩn (Vô hiệu hóa) toàn bộ giá cũ đang active của phòng này
                        section.getPriceTiers().forEach(pt -> pt.setIsActive(false));

                        // Thêm danh sách giá mới vào
                        List<PriceTier> newTiers = secDto.priceTiers().stream().map(ptDto ->
                                PriceTier.builder()
                                        .section(section)
                                        .label(ptDto.label())
                                        .value(ptDto.value())
                                        .unit(ptDto.unit())
                                        .areaUnit(ptDto.areaUnit())
                                        .isActive(true) // Giá mới được active
                                        .build()
                        ).toList();
                        section.getPriceTiers().addAll(newTiers);
                    }
                } else {
                    // Trạng thái Thêm phòng hoàn toàn mới
                    section = WarehouseSection.builder()
                            .warehouse(warehouse)
                            .sector(secDto.sector())
                            .totalCapacity(secDto.totalCapacity())
                            .availableCapacity(secDto.totalCapacity())
                            .tempMin(secDto.tempMin())
                            .tempMax(secDto.tempMax())
                            .humidity(secDto.humidity())
                            .hasCertification(secDto.hasCertification())
                            .priceTiers(new java.util.ArrayList<>())
                            .build();

                    if (secDto.priceTiers() != null) {
                        List<PriceTier> newTiers = secDto.priceTiers().stream().map(ptDto ->
                                PriceTier.builder()
                                        .section(section).label(ptDto.label()).value(ptDto.value())
                                        .unit(ptDto.unit()).areaUnit(ptDto.areaUnit()).isActive(true).build()
                        ).toList();
                        section.getPriceTiers().addAll(newTiers);
                    }
                    warehouse.getSections().add(section);
                }
            }
        }

        // 3. Cập nhật Images
        if (deletedImageIds != null && !deletedImageIds.isEmpty()) {
            List<WarehouseImage> imagesToRemove = warehouse.getImages().stream()
                    .filter(img -> deletedImageIds.contains(img.getId()))
                    .toList();

            for (WarehouseImage img : imagesToRemove) {
                fileStorageService.deleteFile(img.getImageUrl());
                warehouse.getImages().remove(img);
            }
        }

        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            int currentMaxOrder = warehouse.getImages().stream()
                    .mapToInt(WarehouseImage::getDisplayOrder)
                    .max().orElse(-1);

            for (int i = 0; i < newImageUrls.size(); i++) {
                warehouse.getImages().add(WarehouseImage.builder()
                        .warehouse(warehouse)
                        .imageUrl(newImageUrls.get(i))
                        .isThumbnail(false)
                        .displayOrder(currentMaxOrder + 1 + i)
                        .build());
            }
        }

        if (!warehouse.getImages().isEmpty()) {
            warehouse.getImages().sort(java.util.Comparator.comparing(WarehouseImage::getDisplayOrder));
            for (int i = 0; i < warehouse.getImages().size(); i++) {
                warehouse.getImages().get(i).setIsThumbnail(i == 0);
                warehouse.getImages().get(i).setDisplayOrder(i);
            }
        }

        // 4. Cập nhật Certificates
        if (deletedCertIds != null && !deletedCertIds.isEmpty()) {
            List<CertificationSubmit> certsToRemove = warehouse.getCertificationSubmits().stream()
                    .filter(c -> deletedCertIds.contains(c.getId())).toList();
            for (CertificationSubmit cert : certsToRemove) {
                if (cert.getLink() != null) {
                    fileStorageService.deleteFile(cert.getLink());
                }
                warehouse.getCertificationSubmits().remove(cert);
            }
        }

        if (newCertificates != null && !newCertificates.isEmpty()) {
            for (WarehouseCertCreateDTO certDto : newCertificates) {
                CertificationType type = certificationTypeRepository.findById(certDto.certTypeId())
                        .orElseThrow(() -> new RuntimeException("Loại chứng chỉ ID " + certDto.certTypeId() + " không tồn tại!"));

                warehouse.getCertificationSubmits().add(CertificationSubmit.builder()
                        .warehouse(warehouse)
                        .type(type)
                        .link(certDto.link())
                        .status(com.ailogis.api.enums.VerifyStatus.PENDING)
                        .build());
            }
        }
        return warehouseMapper.toWarehouseResponseDTO(warehouseRepository.save(warehouse));
    }

    // Trong OwnerService.java (Nhớ Inject thêm PaymentService)
    @Transactional
    public PaymentResponseDTO buySponsorTier(Long ownerId, Long warehouseId, BuySponsorRequestDTO dto, HttpServletRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thao tác trên kho bãi này!");
        }

        SponsorTier sponsorTier = sponsorTierRepository.findById(dto.sponsorTierId())
                .orElseThrow(() -> new RuntimeException("Gói tài trợ không tồn tại!"));

        // Tạo Transaction nháp (PENDING)
        Transaction transaction = Transaction.builder()
                .buyer(warehouse.getOwner())
                .warehouse(warehouse) // Lưu thông tin kho bãi cần nâng cấp
                .sponsor(sponsorTier)
                .amount(sponsorTier.getPricingPerMonth())
                .type("SPONSOR_SUBSCRIPTION")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .invoiceDate(LocalDateTime.now())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // Sinh link VNPay
        String paymentUrl = paymentService.createVNPayUrl(savedTx, request);

        return new PaymentResponseDTO(paymentUrl);
    }

    public Page<RentRequestResponseDTO> getWarehouseRentRequests(Long ownerId, Long warehouseId, String statusStr, Pageable pageable) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền xem dữ liệu của kho bãi này!");
        }

        RequestStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = RequestStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái Request không hợp lệ!");
            }
        }

        return requestRepository.findByWarehouseIdWithFilter(warehouseId, statusEnum, pageable).map(this::mapToRequestDTO);
    }

    public Page<ContractResponseDTO> getWarehouseContracts(Long ownerId, Long warehouseId, String statusStr, Pageable pageable) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        if (!warehouse.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền xem dữ liệu của kho bãi này!");
        }

        ContractStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = ContractStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái hợp đồng không hợp lệ!");
            }
        }

        return contractRepository.findByWarehouseIdWithFilter(warehouseId, statusEnum, pageable).map(contractMapper::toContractResponseDTO);
    }

    public List<SponsorTierDTO> getSponsorTiersForOwner(Long ownerId) {
        return sponsorTierRepository.findActiveAndPurchasedByOwner(ownerId).stream().map(tier -> {
            long count = warehouseRepository.countBySponsorTypeId(tier.getId());
            return new SponsorTierDTO(tier.getId(), tier.getPriorityLevel(), tier.getPricingPerMonth(),
                    tier.getYearPackSale(), tier.getLabel(), count, tier.getIsActive());
        }).toList();
    }
}