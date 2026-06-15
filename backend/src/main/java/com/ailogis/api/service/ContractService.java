package com.ailogis.api.service;

import com.ailogis.api.dto.ContractAmendDTO;
import com.ailogis.api.dto.ContractCreateDTO;
import com.ailogis.api.dto.ContractResponseDTO;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.ContractStatus;
import com.ailogis.api.enums.RequestStatus;
import com.ailogis.api.enums.Role;
import com.ailogis.api.mapper.ContractMapper;
import com.ailogis.api.repository.ContractRepository;
import com.ailogis.api.repository.RentalRequestRepository;
import com.ailogis.api.repository.WarehouseSectionRepository;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ContractMapper contractMapper;

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
                .owner(owner)
                .renter(renter)
                .request(request)
                .cargoDescription(request.getCargoDescription())
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusMonths(request.getDuration()))
                .ownerLegalName(owner.getCompany() != null ? owner.getCompany().getCompanyName() : owner.getFullName())
                .ownerTaxCode(owner.getCompany() != null ? owner.getCompany().getCompanyTaxCode() : "N/A")
                .ownerEmail(owner.getEmail())
                .ownerPhone(owner.getPhone())
                .renterLegalName(renter.getCompany() != null ? renter.getCompany().getCompanyName() : renter.getFullName())
                .renterTaxCode(renter.getCompany() != null ? renter.getCompany().getCompanyTaxCode() : "N/A")
                .renterEmail(renter.getEmail())
                .renterPhone(renter.getPhone())
                .ownerSigned(true)
                .renterSigned(false)
                .status(ContractStatus.ACTIVE)
                .build();

        Contract saved = contractRepository.save(contract);

        return contractMapper.toContractResponseDTO(saved);
    }

    // Logic hoàn trả diện tích khi Hợp đồng Hủy hoặc Kết thúc
    @Transactional
    public ContractResponseDTO updateContractStatus(Long ownerId, Long contractId, ContractStatus newStatus) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng"));

        if (!contract.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Không có quyền thao tác!");
        }

        if (contract.getStatus() == newStatus) {
            return contractMapper.toContractResponseDTO(contract);
        }

        if (contract.getStatus() == ContractStatus.CANCELED || contract.getStatus() == ContractStatus.COMPLETED) {
            throw new RuntimeException("Hợp đồng đã kết thúc hoặc bị hủy, không thể thay đổi trạng thái!");
        }

        // BẢO VỆ DỮ LIỆU: Nếu đổi sang ACTIVE, phải đảm bảo cả 2 bên đã ký (Từ PENDING -> ACTIVE)
        if (newStatus == ContractStatus.ACTIVE) {
            if (!contract.getOwnerSigned() || !contract.getRenterSigned()) {
                throw new RuntimeException("Không thể kích hoạt hợp đồng khi chưa có đủ chữ ký xác nhận của cả Chủ kho và Khách thuê!");
            }

            // Tự động hủy hợp đồng gốc nếu đây là bản phụ lục sửa đổi (Amendment)
            if (contract.getParentContractId() != null) {
                Contract parentContract = contractRepository.findById(contract.getParentContractId()).orElse(null);
                if (parentContract != null && parentContract.getStatus() == ContractStatus.ACTIVE) {
                    parentContract.setStatus(ContractStatus.CANCELED);
                    parentContract.setCancelReason("Bị thay thế bởi phụ lục hợp đồng (Amendment) ID: " + contract.getId());
                    contractRepository.save(parentContract);
                }
            }
        }

        contract.setStatus(newStatus);
        Contract updated = contractRepository.save(contract);

        return contractMapper.toContractResponseDTO(updated);
    }

    public Page<ContractResponseDTO> getMyContracts(CustomUserDetails userDetails, String statusStr, Pageable pageable) {
        Long userId = userDetails.getUser().getId();
        Role role = userDetails.getUser().getRole();
        ContractStatus statusEnum = null;
        if (statusStr != null && !statusStr.isBlank()) {
            statusEnum = ContractStatus.valueOf(statusStr.toUpperCase());
        }

        Page<Contract> contracts;
        if (role == Role.EMPLOYEE) {
            contracts = contractRepository.findAllWithFilter(statusEnum, pageable);
        } else {
            contracts = contractRepository.findByOwnerIdOrRenterIdWithFilter(userId, statusEnum, pageable);
        }
        return contracts.map(contractMapper::toContractResponseDTO);
    }

    public ContractResponseDTO getContractDetail(Long id, CustomUserDetails userDetails) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng này!"));

        Long renterId = contract.getRenter().getId();
        Long ownerId = contract.getOwner().getId();

        verifyAccess(userDetails, ownerId, renterId);

        return mapToResponseDTO(contract);
    }

    @Transactional
    public ContractResponseDTO amendContract(Long ownerId, Long oldContractId, ContractAmendDTO dto) {
        Contract oldContract = contractRepository.findById(oldContractId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng cũ!"));

        if (!oldContract.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Bạn không có quyền sửa đổi hợp đồng này!");
        }

        if (oldContract.getStatus() != com.ailogis.api.enums.ContractStatus.ACTIVE) {
            throw new RuntimeException("Chỉ có thể tạo phụ lục/sửa đổi cho hợp đồng đang ở trạng thái ACTIVE!");
        }

        // Tạo hợp đồng mới (Bản sao kế thừa bản cũ)
        Contract newContract = Contract.builder()
                .request(oldContract.getRequest())
                .owner(oldContract.getOwner())
                .renter(oldContract.getRenter())
                .cargoDescription(oldContract.getCargoDescription())

                // Cập nhật các trường nếu Owner có truyền lên, không thì giữ nguyên bản cũ
                .startAt(dto.startAt() != null ? dto.startAt() : oldContract.getStartAt())
                .endAt(dto.endAt() != null ? dto.endAt() : oldContract.getEndAt())
                .paymentTerm(dto.paymentTerm() != null ? dto.paymentTerm() : oldContract.getPaymentTerm())
                .penaltyClause(dto.penaltyClause() != null ? dto.penaltyClause() : oldContract.getPenaltyClause())
                .specialTerm(dto.specialTerm() != null ? dto.specialTerm() : oldContract.getSpecialTerm())

                // Lấy lại các data từ hợp đồng cũ
                .ownerLegalName(oldContract.getOwnerLegalName())
                .ownerTaxCode(oldContract.getOwnerTaxCode())
                .ownerEmail(oldContract.getOwnerEmail())
                .ownerPhone(oldContract.getOwnerPhone())
                .ownerAddress(oldContract.getOwnerAddress())
                .renterLegalName(oldContract.getRenterLegalName())
                .renterTaxCode(oldContract.getRenterTaxCode())
                .renterEmail(oldContract.getRenterEmail())
                .renterPhone(oldContract.getRenterPhone())
                .renterAddress(oldContract.getRenterAddress())

                .ownerSigned(true)
                .renterSigned(false)

                // Set trạng thái chờ Renter đồng ý
                .status(ContractStatus.PENDING)
                .parentContractId(oldContract.getId())
                .build();

        return contractMapper.toContractResponseDTO(contractRepository.save(newContract));
    }

    @Transactional
    public ContractResponseDTO signContract(Long renterId, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng!"));

        if (!contract.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Lỗi bảo mật: Chỉ Khách thuê của hợp đồng này mới có quyền ký xác nhận!");
        }

        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể ký xác nhận khi hợp đồng đang ở trạng thái PENDING!");
        }

        contract.setRenterSigned(true);

        if (contract.getOwnerSigned() && contract.getRenterSigned()) {
            contract.setStatus(ContractStatus.ACTIVE);

            if (contract.getParentContractId() != null) {
                Contract parentContract = contractRepository.findById(contract.getParentContractId()).orElse(null);
                if (parentContract != null && parentContract.getStatus() == ContractStatus.ACTIVE) {
                    parentContract.setStatus(ContractStatus.CANCELED);
                    parentContract.setCancelReason("Bị thay thế bởi phụ lục hợp đồng (Amendment) ID: " + contract.getId());
                    contractRepository.save(parentContract);
                }
            }
        }

        return contractMapper.toContractResponseDTO(contractRepository.save(contract));
    }

    @Transactional
    public ContractResponseDTO rejectContract(Long renterId, Long contractId, String reason) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hợp đồng!"));

        if (!contract.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Lỗi bảo mật: Chỉ Khách thuê của hợp đồng này mới có quyền từ chối!");
        }

        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể từ chối khi hợp đồng đang ở trạng thái chờ ký (PENDING)!");
        }

        contract.setStatus(ContractStatus.CANCELED);
        contract.setCancelReason(reason != null ? "Khách thuê từ chối ký: " + reason : "Khách thuê không đồng ý với các điều khoản trong hợp đồng.");

        return contractMapper.toContractResponseDTO(contractRepository.save(contract));
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
                c.getId(),
                c.getRequest() != null ? c.getRequest().getId() : null,
                (c.getRequest() != null && c.getRequest().getWarehouse() != null) ? c.getRequest().getWarehouse().getName() : "N/A",
                c.getCargoDescription(),
                c.getStartAt(),
                c.getEndAt(),
                c.getPaymentTerm(),
                c.getPenaltyClause(),
                c.getSpecialTerm(),
                c.getCancelReason(),

                c.getOwnerLegalName(),
                c.getOwnerTaxCode(),
                c.getOwnerEmail(),
                c.getOwnerPhone(),
                c.getOwnerAddress(),

                c.getRenterLegalName(),
                c.getRenterTaxCode(),
                c.getRenterEmail(),
                c.getRenterPhone(),
                c.getRenterAddress(),

                (c.getRequest() != null && c.getRequest().getOfferedPrice() != null) ? c.getRequest().getOfferedPrice().longValue() : 0L,
                c.getStatus() != null ? c.getStatus().name() : null
        );
    }
}