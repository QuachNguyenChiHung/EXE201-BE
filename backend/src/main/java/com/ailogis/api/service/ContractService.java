package com.ailogis.api.service;

import com.ailogis.api.dto.ContractCreateDTO;
import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.ContractStatus;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.Role;
import com.ailogis.api.repository.ContractRepository;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.repository.WarehouseSectionRepository;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final RentalRequestRepository requestRepository;
    private final WarehouseSectionRepository sectionRepository;

    @Transactional
    public ContractResponseDTO createContract(Long ownerId, ContractCreateDTO dto) {
        RentalRequest request = requestRepository.findById(dto.requestId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn yêu cầu"));

        User owner = request.getWarehouse().getOwner();
        User renter = request.getRenter();

        if (!owner.getId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền ký hợp đồng cho kho này!");
        }
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new RuntimeException("Đơn yêu cầu thuê chưa được phê duyệt!");
        }

        // Vòng lặp qua từng phòng được đặt thuê để kiểm tra và trừ available_capacity
        for (RentRequestDetail detail : request.getDetails()) {
            WarehouseSection section = detail.getSection();
            if (section.getAvailableCapacity() < detail.getRentedArea()) {
                throw new RuntimeException("Phòng số " + section.getSector() + " không đủ chỗ trống để kích hoạt hợp đồng!");
            }
            section.setAvailableCapacity(section.getAvailableCapacity() - detail.getRentedArea());
            sectionRepository.save(section); // Cập nhật lại sức chứa phòng
        }

        // Lưu bản chụp Snapshot thông tin pháp lý bất biến vào hợp đồng
        Contract contract = Contract.builder()
                .owner(owner).renter(renter).request(request)
                .cargoDescription(request.getCargoDescription()).startAt(LocalDate.now()).endAt(LocalDate.now().plusMonths(request.getDuration()))
                .ownerLegalName(owner.getCompany() != null ? owner.getCompany().getCompanyName() : owner.getFullName())
                .ownerTaxCode(owner.getCompany() != null ? owner.getCompany().getCompanyTaxCode() : "N/A")
                .ownerEmail(owner.getEmail()).ownerPhone(owner.getPhone())
                .renterLegalName(renter.getCompany() != null ? renter.getCompany().getCompanyName() : renter.getFullName())
                .renterTaxCode(renter.getCompany() != null ? renter.getCompany().getCompanyTaxCode() : "N/A")
                .renterEmail(renter.getEmail()).renterPhone(renter.getPhone())
                .status(ContractStatus.ACTIVE).build();

        Contract saved = contractRepository.save(contract);
        return new ContractResponseDTO(saved.getId(), request.getId(), request.getWarehouse().getName(), renter.getFullName(), dto.totalPrice(), LocalDate.now(), saved.getStatus().name());
    }


    // Logic hoàn trả diện tích khi Hợp đồng Hủy hoặc Kết thúc
    @Transactional
    public ContractResponseDTO updateContractStatus(Long ownerId, Long contractId, ContractStatus newStatus) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng"));

        if (!contract.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Không có quyền thao tác!");
        }

        if (newStatus == ContractStatus.CANCELED || newStatus == ContractStatus.COMPLETED) {
            if (contract.getStatus() == ContractStatus.ACTIVE) {
                for (RentRequestDetail detail : contract.getRequest().getDetails()) {
                    WarehouseSection section = detail.getSection();
                    section.setAvailableCapacity(section.getAvailableCapacity() + detail.getRentedArea());
                    sectionRepository.save(section);
                }
            }
        }

        contract.setStatus(newStatus);
        Contract updated = contractRepository.save(contract);

        return new ContractResponseDTO(
                updated.getId(),
                contract.getRequest().getId(),
                contract.getRequest().getWarehouse().getName(),
                contract.getRenter().getFullName(),
                0L,
                contract.getStartAt(),
                updated.getStatus().name()
        );
    }

    public List<ContractResponseDTO> getMyContracts(CustomUserDetails userDetails, String statusStr) {
        Long userId = userDetails.getUser().getId();
        com.ailogis.api.enums.Role role = userDetails.getUser().getRole();

        ContractStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusEnum = ContractStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái hợp đồng không hợp lệ!");
            }
        }

        List<Contract> contracts;

        if (role == com.ailogis.api.enums.Role.EMPLOYEE) {
            contracts = contractRepository.findAllWithFilter(statusEnum);
        } else {
            contracts = contractRepository.findByOwnerIdOrRenterIdWithFilter(userId, statusEnum);
        }

        return contracts.stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    public ContractResponseDTO getContractDetail(Long id, CustomUserDetails userDetails) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng này!"));

        Long renterId = contract.getRenter().getId();
        Long ownerId = contract.getOwner().getId();

        verifyAccess(userDetails, ownerId, renterId);

        return new ContractResponseDTO(
                contract.getId(),
                contract.getRequest().getId(),
                contract.getRequest().getWarehouse().getName(),
                contract.getRenter().getFullName(),
                contract.getRequest().getOfferedPrice() != null ? contract.getRequest().getOfferedPrice().longValue() : 0L,
                contract.getStartAt(),
                contract.getStatus().name()
        );
    }

    private void verifyAccess(CustomUserDetails userDetails, Long ownerId, Long renterId) {
        Long currentUserId = userDetails.getUser().getId();
        Role role = userDetails.getUser().getRole();

        if (role == Role.EMPLOYEE) return;

        if (currentUserId.equals(ownerId) || currentUserId.equals(renterId)) return;

        throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền truy cập vào dữ liệu này!");
    }

    private ContractResponseDTO mapToResponseDTO(Contract c) {
        return new ContractResponseDTO(
                c.getId(), c.getRequest().getId(), c.getRequest().getWarehouse().getName(),
                c.getRenter().getFullName(),
                c.getRequest().getOfferedPrice() != null ? c.getRequest().getOfferedPrice().longValue() : 0L,
                c.getStartAt(), c.getStatus().name()
        );
    }
}