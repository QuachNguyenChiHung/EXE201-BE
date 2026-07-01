package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.Role;
import com.ailogis.api.repository.*;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

        if (warehouse.getStatus() == com.ailogis.api.enums.WarehouseStatus.INACTIVE ||
                warehouse.getStatus() == com.ailogis.api.enums.WarehouseStatus.REJECTED) {
            throw new RuntimeException("Kho bãi này hiện không hoạt động, không thể tạo yêu cầu thuê mới!");
        }

        RentalRequest request = RentalRequest.builder()
                .renter(renter).warehouse(warehouse).cargoDescription(dto.cargoDescription())
                .otherDetail(dto.otherDetail()).duration(dto.duration()).durationUnit(dto.durationUnit())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .renterOfferedPrice(dto.renterOfferedPrice())
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

    public Page<RentRequestResponseDTO> getRequestsByRenter(Long renterId, String statusStr, Pageable pageable) {
        com.ailogis.api.enums.RequestStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            statusEnum = com.ailogis.api.enums.RequestStatus.valueOf(statusStr.toUpperCase());
        }
        return requestRepository.findByRenterIdWithFilter(renterId, statusEnum, pageable).map(this::mapToResponseDTO);
    }

    private RentRequestResponseDTO mapToResponseDTO(RentalRequest r) {
        List<RentRequestDetailResponseDTO> detailDTOs = r.getDetails().stream().<RentRequestDetailResponseDTO>map(d ->
                new RentRequestDetailResponseDTO(d.getId(), d.getSection().getSector(), d.getPriceTier().getLabel(), d.getPriceTier().getValue(), d.getRentedArea(), d.getAreaUnit(), d.getSection().getTempMin(), d.getSection().getTempMax(), d.getSection().getHumidity())
        ).toList();

        return new RentRequestResponseDTO(
                r.getId(),
                r.getWarehouse().getId(),
                r.getWarehouse().getName(),
                r.getRenter() != null ? r.getRenter().getFullName() : "N/A",
                r.getRenter() != null && r.getRenter().getCompany() != null ? r.getRenter().getCompany().getCompanyName() : null,
                r.getRenter() != null && r.getRenter().getCompany() != null ? r.getRenter().getCompany().getCompanyTaxCode() : null,
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
                r.getRenterNote(),
                detailDTOs
        );
    }

    @Transactional
    public RentRequestResponseDTO cancelRequestByRenter(Long renterId, Long requestId, String reason) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê!"));

        if (!request.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Bạn không có quyền thao tác!");
        }

        if (request.getStatus() != com.ailogis.api.enums.RequestStatus.PENDING &&
            request.getStatus() != com.ailogis.api.enums.RequestStatus.NEGOTIATING) {
            throw new RuntimeException("Chỉ có thể hủy yêu cầu khi đang chờ duyệt hoặc đang thương lượng!");
        }

        request.setStatus(com.ailogis.api.enums.RequestStatus.REJECTED);
        request.setRenterRejectionReason(reason != null ? reason : "Người thuê tự hủy");

        return mapToResponseDTO(requestRepository.save(request));
    }

    @Transactional
    public RentRequestResponseDTO acceptOffer(Long renterId, Long requestId) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê!"));

        if (!request.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Bạn không có quyền thao tác!");
        }

        if (request.getStatus() != com.ailogis.api.enums.RequestStatus.PENDING &&
            request.getStatus() != com.ailogis.api.enums.RequestStatus.NEGOTIATING) {
            throw new RuntimeException("Yêu cầu này không thể được chấp nhận!");
        }

        if (request.getOfferedPrice() == null) {
            throw new RuntimeException("Không có giá đề xuất nào để chấp nhận!");
        }

        request.setStatus(com.ailogis.api.enums.RequestStatus.APPROVED);
        return mapToResponseDTO(requestRepository.save(request));
    }

    @Transactional
    public RentRequestResponseDTO counterOffer(Long renterId, Long requestId, String note, Double newPrice) {
        RentalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê!"));

        if (!request.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Bạn không có quyền thao tác!");
        }

        if (request.getStatus() != com.ailogis.api.enums.RequestStatus.PENDING &&
            request.getStatus() != com.ailogis.api.enums.RequestStatus.NEGOTIATING) {
            throw new RuntimeException("Yêu cầu này không thể phản đề xuất!");
        }

        if (newPrice == null || newPrice <= 0) {
            throw new RuntimeException("Giá mới là bắt buộc!");
        }

        // Clear owner's offered price and set renter's new counter price
        request.setOfferedPrice(null);
        request.setRenterOfferedPrice(newPrice);
        request.setRenterNote(note);
        request.setStatus(com.ailogis.api.enums.RequestStatus.NEGOTIATING);

        return mapToResponseDTO(requestRepository.save(request));
    }

    // Kiểm tra xem có phải là Employee hoặc Owner/Renter liên quan đến Data
    public RentRequestResponseDTO getRequestDetail(Long id, CustomUserDetails userDetails) {
        RentalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thuê này!"));

        Long renterId = request.getRenter().getId();
        Long ownerId = request.getWarehouse().getOwner().getId();

        verifyAccess(userDetails, ownerId, renterId);

        return mapToResponseDTO(request);
    }

    private void verifyAccess(CustomUserDetails userDetails, Long ownerId, Long renterId) {
        Long currentUserId = userDetails.getUser().getId();
        Role role = userDetails.getUser().getRole();

        // 1. Employee được xem mọi thứ
        if (role == Role.EMPLOYEE) return;

        // 2. Owner hoặc Renter liên quan trực tiếp đến Data này mới được xem
        if (currentUserId.equals(ownerId) || currentUserId.equals(renterId)) return;

        // 3. Các trường hợp còn lại sẽ bị chặn tại đây (Chống IDOR)
        throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền truy cập vào dữ liệu này!");
    }
}