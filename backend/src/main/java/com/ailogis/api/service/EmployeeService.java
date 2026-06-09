package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Company;
import com.ailogis.api.entity.User;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.entity.WarehouseSection;
import com.ailogis.api.enums.*;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RentalRequestRepository rentalRequestRepository;
    private final ContractRepository contractRepository;
    private final CompanyRepository companyRepository;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserDTO(
                        u.getId(), u.getEmail(), u.getFullName(),
                        u.getCompany() != null ? u.getCompany().getCompanyName() : "Cá nhân",
                        u.getRole().name(), u.getStatus().name()
                )).toList();
    }

    public List<WarehouseResponseDTO> getPendingWarehouses() {
        return warehouseRepository.findByStatus(WarehouseStatus.PENDING).stream()
                .map(this::mapToWarehouseDTO).toList();
    }

    @Transactional
    public WarehouseResponseDTO verifyWarehouse(Long warehouseId, WarehouseStatus newStatus) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        warehouse.setStatus(newStatus);
        Warehouse updated = warehouseRepository.save(warehouse);
        return mapToWarehouseDTO(updated);
    }

    private WarehouseResponseDTO mapToWarehouseDTO(Warehouse w) {
        // 1. Map danh sách các phòng (có check null)
        List<WarehouseSectionDTO> sectionDTOs = w.getSections() != null ?
                w.getSections().stream().map(s -> new WarehouseSectionDTO(
                        s.getSector(), s.getTotalCapacity(), s.getTempMin(), s.getTempMax(), s.getHumidity(), s.getHasCertification(),
                        s.getPriceTiers() != null ? s.getPriceTiers().stream().map(p -> new PriceTierDTO(p.getLabel(), p.getValue(), p.getUnit(), p.getAreaUnit())).toList() : List.of()
                )).toList() : List.of();

        // 2. Map danh sách ảnh (có check null an toàn)
        List<WarehouseImageDTO> imageDTOs = w.getImages() != null ?
                w.getImages().stream().map(i -> new WarehouseImageDTO(i.getId(), i.getImageUrl(), i.getIsThumbnail())).toList() : List.of();

        // 3. BỔ SUNG: Map danh sách chứng chỉ PDF
        List<CertificationSubmitDTO> certDTOs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(c.getId(), c.getType().getLabel(), c.getLink(), c.getIsVerified())
                ).toList() : List.of();

        // 4. Trả về DTO (Đã truyền đủ certDTOs vào constructor)
        return new WarehouseResponseDTO(
                w.getId(), w.getName(), w.getDescription(), w.getLocationAddressText(),
                w.getLocationProvince(), w.getLocationCommune(), sectionDTOs, imageDTOs, certDTOs, w.getStatus().name()
        );
    }

    public StatisticResponseDTO getGlobalStatistics() {
        long totalUsers = userRepository.count();

        Map<String, Long> usersByRole = Map.of(
                "renter", userRepository.countByRole(Role.RENTER),
                "warehouse", userRepository.countByRole(Role.OWNER),
                "employee", userRepository.countByRole(Role.EMPLOYEE)
        );

        Map<String, Long> warehousesByStatus = Map.of(
                "active", warehouseRepository.countByStatus(WarehouseStatus.ACTIVE),
                "rented", warehouseRepository.countByStatus(WarehouseStatus.RENTED),
                "pending", warehouseRepository.countByStatus(WarehouseStatus.PENDING),
                "rejected", warehouseRepository.countByStatus(WarehouseStatus.REJECTED),
                "inactive", warehouseRepository.countByStatus(WarehouseStatus.INACTIVE)
        );

        Map<String, Long> rentRequestsByStatus = Map.of(
                "inprogress", rentalRequestRepository.countByStatus(RequestStatus.PENDING),
                "completed", rentalRequestRepository.countByStatus(RequestStatus.APPROVED),
                "cancelled", rentalRequestRepository.countByStatus(RequestStatus.REJECTED)
        );

        Map<String, Long> contractsByStatus = Map.of(
                "active", contractRepository.countByStatus(ContractStatus.ACTIVE),
                "ended", contractRepository.countByStatus(ContractStatus.COMPLETED)
        );

        return new StatisticResponseDTO(totalUsers, usersByRole, warehousesByStatus, rentRequestsByStatus, contractsByStatus);
    }

    public UserStatisticResponseDTO getUsersStatistics() {
        Map<String, Long> usersByRole = Map.of(
                "renter", userRepository.countByRole(Role.RENTER),
                "warehouse", userRepository.countByRole(Role.OWNER),
                "employee", userRepository.countByRole(Role.EMPLOYEE)
        );
        return new UserStatisticResponseDTO(usersByRole);
    }

    public List<UserDTO> searchUsers(String keyword) {
        return userRepository.searchUsers(keyword).stream()
                .map(u -> new UserDTO(
                        u.getId(), u.getEmail(), u.getFullName(),
                        u.getCompany() != null ? u.getCompany().getCompanyName() : "Cá nhân",
                        u.getRole().name(), u.getStatus().name()
                )).toList();
    }

    @Transactional
    public UserDTO createEmployee(UserCreateUpdateDTO dto) {
        // Lưu ý: Trong thực tế sẽ dùng passwordEncoder.encode(dto.password())
        User newUser = User.builder()
                .fullName(dto.name())
                .email(dto.email())
                .password(dto.password()) // Hiện tại đang lưu plain text
                .phone(dto.phone())
                .role(Role.valueOf(dto.role().toUpperCase()))
                .status(UserStatus.valueOf(dto.status().toUpperCase()))
                .avatarUrl(dto.imgLink())
                .hashTaxCode(dto.hashTaxCode())
                .build();

        // Gắn company nếu có truyền lên
        if (dto.companyID() != null) {
            Company company = companyRepository.findById(dto.companyID())
                    .orElseThrow(() -> new RuntimeException("Công ty không tồn tại!"));
            newUser.setCompany(company);
        }

        User saved = userRepository.save(newUser);
        return new UserDTO(saved.getId(), saved.getEmail(), saved.getFullName(),
                saved.getCompany() != null ? saved.getCompany().getCompanyName() : null,
                saved.getRole().name(), saved.getStatus().name());
    }

    @Transactional
    public UserDTO updateUser(Long userId, UserCreateUpdateDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (dto.name() != null) user.setFullName(dto.name());
        if (dto.phone() != null) user.setPhone(dto.phone());
        if (dto.status() != null) user.setStatus(com.ailogis.api.enums.UserStatus.valueOf(dto.status().toUpperCase()));
        if (dto.imgLink() != null) user.setAvatarUrl(dto.imgLink());
        if (dto.hashTaxCode() != null) user.setHashTaxCode(dto.hashTaxCode());

        User updated = userRepository.save(user);
        return new UserDTO(updated.getId(), updated.getEmail(), updated.getFullName(),
                updated.getCompany() != null ? updated.getCompany().getCompanyName() : null,
                updated.getRole().name(), updated.getStatus().name());
    }

    public List<WarehouseEmployeeDTO> getWarehousesByStatus(WarehouseStatus status) {
        List<Warehouse> warehouses = (status == null) ?
                warehouseRepository.findAll() : warehouseRepository.findByStatus(status);

        return warehouses.stream().map(this::mapToEmployeeDTO).toList();
    }

    @Transactional
    public WarehouseEmployeeDTO changeWarehouseStatus(Long warehouseId, WarehouseStatus newStatus) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        warehouse.setStatus(newStatus);
        if (newStatus == WarehouseStatus.ACTIVE && isFullyRented(warehouse)) {
            warehouse.setStatus(WarehouseStatus.RENTED);
        }

        return mapToEmployeeDTO(warehouseRepository.save(warehouse));
    }

    private WarehouseEmployeeDTO mapToEmployeeDTO(Warehouse w) {
        double totalCap = 0;
        double availableCap = 0;
        double tempMin = Double.MAX_VALUE;
        double tempMax = Double.MIN_VALUE;
        double minPrice = 0.0;

        List<Map<String, Object>> sections = new java.util.ArrayList<>();

        if (w.getSections() != null && !w.getSections().isEmpty()) {
            for (WarehouseSection s : w.getSections()) {
                totalCap += s.getTotalCapacity() != null ? s.getTotalCapacity() : 0;
                availableCap += s.getAvailableCapacity() != null ? s.getAvailableCapacity() : 0;
                if (s.getTempMin() != null && s.getTempMin() < tempMin) tempMin = s.getTempMin();
                if (s.getTempMax() != null && s.getTempMax() > tempMax) tempMax = s.getTempMax();

                sections.add(Map.of(
                        "id_section", s.getId(),
                        "name", s.getLabel() != null ? s.getLabel() : "Khu " + s.getSector(),
                        "temp_min", s.getTempMin(),
                        "temp_max", s.getTempMax(),
                        "total_capacity", s.getTotalCapacity()
                ));
            }
        }

        if (tempMin == Double.MAX_VALUE) tempMin = 0.0;
        if (tempMax == Double.MIN_VALUE) tempMax = 0.0;

        Map<String, Object> stats = Map.of(
                "totalCapacity", totalCap,
                "availableCapacity", availableCap,
                "temperatureMin", tempMin,
                "temperatureMax", tempMax,
                "securityLevel", "high" // Hardcode tạm thời theo JSON mẫu
        );

        List<CertificationSubmitDTO> certs = w.getCertificationSubmits() != null ?
                w.getCertificationSubmits().stream().map(c ->
                        new CertificationSubmitDTO(c.getId(), c.getType().getLabel(), c.getLink(), c.getIsVerified())
                ).toList() : List.of();

        return new WarehouseEmployeeDTO(
                w.getId(), w.getOwner().getId(), w.getName(), w.getLocationAddressText(),
                w.getLocationCommune(), w.getLocationProvince(),
                w.getStatus().name().toLowerCase(),
                w.getOwner().getCompany() != null ? w.getOwner().getCompany().getCompanyName() : w.getOwner().getFullName(),
                minPrice, stats, sections, certs
        );
    }

    private boolean isFullyRented(Warehouse w) {
        if (w.getSections() == null || w.getSections().isEmpty()) return false;
        return w.getSections().stream()
                .allMatch(s -> s.getAvailableCapacity() != null && s.getAvailableCapacity() <= 0);
    }
}