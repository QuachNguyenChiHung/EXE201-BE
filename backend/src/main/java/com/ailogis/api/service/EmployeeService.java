package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.*;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final UserSessionRepository userSessionRepository;
    private final WarehouseMapper warehouseMapper;
    private final CertificationSubmitRepository certificationSubmitRepository;
    private final CertificationTypeRepository certificationTypeRepository;
    private final TransactionRepository transactionRepository;

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
                .map(warehouseMapper::toWarehouseResponseDTO)
                .toList();
    }

    @Transactional
    public WarehouseResponseDTO verifyWarehouse(Long warehouseId, WarehouseStatus newStatus) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        warehouse.setStatus(newStatus);
        Warehouse updated = warehouseRepository.save(warehouse);
            return warehouseMapper.toWarehouseResponseDTO(updated);
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

    public long getActiveUsersCount(int days) {
        if (days <= 0) days = 1; // Mặc định ít nhất là 1 ngày
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return userSessionRepository.countActiveUsersSince(since);
    }

    @Transactional
    public CertificationSubmitDTO reviewWarehouseCertification(Long submitId, CertReviewDTO dto) {
        // 1. Tìm hồ sơ chứng chỉ đã nộp
        CertificationSubmit submit = certificationSubmitRepository.findById(submitId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu yêu cầu chứng chỉ này!"));

        // 2. Cập nhật trạng thái duyệt
        submit.setIsVerified(dto.isVerified());

        // 3. Nếu duyệt hợp lệ và có truyền typeId, tiến hành gán loại chứng chỉ chuẩn
        if (Boolean.TRUE.equals(dto.isVerified()) && dto.typeId() != null) {
            CertificationType type = certificationTypeRepository.findById(dto.typeId())
                    .orElseThrow(() -> new RuntimeException("Loại chứng chỉ chỉ định không tồn tại trong hệ thống!"));
            submit.setType(type);
        }

        // 4. Lưu lại và trả về DTO
        CertificationSubmit saved = certificationSubmitRepository.save(submit);
        return new CertificationSubmitDTO(
                saved.getId(),
                saved.getType() != null ? saved.getType().getLabel() : "Chưa phân loại",
                saved.getLink(),
                saved.getIsVerified()
        );
    }

    @Transactional
    public UserDTO updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));
        user.setStatus(UserStatus.valueOf(status.toUpperCase()));
        userRepository.save(user);
        return new UserDTO(user.getId(), user.getEmail(), user.getFullName(),
                user.getCompany() != null ? user.getCompany().getCompanyName() : null,
                user.getRole().name(), user.getStatus().name());
    }

    public Map<String, Object> getActiveUsersByDate(LocalDate startDate, LocalDate endDate) {
        List<Object[]> rawStats = userSessionRepository.countActiveUsersByDateAndRole(startDate, endDate);
        return processTimeSeriesData(rawStats, startDate, endDate, true);
    }

    public Map<String, Object> getActiveUsersByHour(LocalDate date) {
        List<Object[]> rawStats = userSessionRepository.countActiveUsersByHourAndRoleNative(date);
        return processTimeSeriesData(rawStats, date, date, false);
    }

    private Map<String, Object> processTimeSeriesData(List<Object[]> rawStats, LocalDate start, LocalDate end, boolean isDaily) {
        List<String> labels = new java.util.ArrayList<>();
        // Sinh mảng labels (các ngày hoặc các giờ 0-23)
        if (isDaily) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) labels.add(d.toString());
        } else {
            for (int i = 0; i < 24; i++) labels.add(String.valueOf(i));
        }

        Map<String, Map<String, Long>> roleDataMap = Map.of(
                Role.RENTER.name(), new java.util.HashMap<>(),
                Role.OWNER.name(), new java.util.HashMap<>(),
                Role.EMPLOYEE.name(), new java.util.HashMap<>()
        );

        for (Object[] row : rawStats) {
            String label = isDaily ? (String) row[0] : String.valueOf(((Number) row[0]).intValue());
            String roleStr = row[1].toString();
            Long count = ((Number) row[2]).longValue();

            if (roleDataMap.containsKey(roleStr)) {
                roleDataMap.get(roleStr).put(label, count);
            }
        }

        List<ChartSeriesDTO> series = new java.util.ArrayList<>();
        for (String role : roleDataMap.keySet()) {
            List<Long> data = new java.util.ArrayList<>();
            for (String label : labels) {
                data.add(roleDataMap.get(role).getOrDefault(label, 0L));
            }
            series.add(new ChartSeriesDTO(role, data));
        }

        return Map.of(isDaily ? "dates" : "hours", labels, "series", series);
    }

    // DETAIL RENTER
    @Transactional(readOnly = true)
    public RenterDetailResponseDTO getRenterDetail(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy User"));
        if (user.getRole() != Role.RENTER) throw new RuntimeException("User này không phải là RENTER!");

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getFullName(), user.getCompany() != null ? user.getCompany().getCompanyName() : null, user.getRole().name(), user.getStatus().name());
        String aiPlan = user.getAiTier() != null ? user.getAiTier().getLabel() : "Chưa đăng ký";

        // 1. Lấy Requests
        List<RentRequestResponseDTO> requests = rentalRequestRepository.findByRenterId(userId).stream()
                .map(r -> new RentRequestResponseDTO(r.getId(), r.getWarehouse().getName(), r.getCargoDescription(), r.getDuration(), r.getDurationUnit(), r.getStatus().name(), List.of())).toList();

        // 2. Lấy Contracts (Cần thêm findByRenterId trong ContractRepository nếu chưa có)
        // Tạm gọi repository lấy toàn bộ rồi filter (Tốt nhất bạn thêm query vào ContractRepo)
        List<ContractResponseDTO> contracts = contractRepository.findAll().stream()
                .filter(c -> c.getRenter().getId().equals(userId))
                .map(c -> new ContractResponseDTO(c.getId(), c.getRequest().getId(), c.getRequest().getWarehouse().getName(), c.getRenter().getFullName(), c.getRequest().getOfferedPrice() != null ? c.getRequest().getOfferedPrice().longValue() : 0L, c.getStartAt(), c.getStatus().name())).toList();

        double totalSpending = 0;
        List<Transaction> transactions = transactionRepository.findByBuyerIdAndStatus(userId, "COMPLETED");
        for(Transaction t : transactions) {
            if(t.getSubscription() != null && t.getSubscription().getPrice() != null) totalSpending += t.getSubscription().getPrice();
            if(t.getSponsor() != null && t.getSponsor().getPricingPerMonth() != null) totalSpending += t.getSponsor().getPricingPerMonth();
        }

        return new RenterDetailResponseDTO(userDTO, aiPlan, requests, contracts, totalSpending);
    }

    // DETAIL OWNER
    @Transactional(readOnly = true)
    public OwnerDetailResponseDTO getOwnerDetail(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy User"));
        if (user.getRole() != Role.OWNER) throw new RuntimeException("User này không phải là OWNER!");

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getFullName(), user.getCompany() != null ? user.getCompany().getCompanyName() : null, user.getRole().name(), user.getStatus().name());

        List<WarehouseResponseDTO> warehouses = warehouseRepository.findByOwnerId(userId).stream().map(warehouseMapper::toWarehouseResponseDTO).toList();

        List<RentRequestResponseDTO> requests = rentalRequestRepository.findByWarehouseOwnerId(userId).stream()
                .map(r -> new RentRequestResponseDTO(r.getId(), r.getWarehouse().getName(), r.getCargoDescription(), r.getDuration(), r.getDurationUnit(), r.getStatus().name(), List.of())).toList();

        List<ContractResponseDTO> contracts = contractRepository.findAll().stream()
                .filter(c -> c.getOwner().getId().equals(userId))
                .map(c -> new ContractResponseDTO(c.getId(), c.getRequest().getId(), c.getRequest().getWarehouse().getName(), c.getRenter().getFullName(), c.getRequest().getOfferedPrice() != null ? c.getRequest().getOfferedPrice().longValue() : 0L, c.getStartAt(), c.getStatus().name())).toList();

        return new OwnerDetailResponseDTO(userDTO, warehouses, requests, contracts);
    }


    public Map<String, Object> getUserActivityStats(Long userId, int days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (days <= 0) days = 7; // Mặc định 7 ngày
        java.time.LocalDate endDate = java.time.LocalDate.now();
        java.time.LocalDate startDate = endDate.minusDays(days - 1);

        List<Object[]> rawStats = userSessionRepository.countLoginsByDateForUser(userId, startDate);

        Map<String, Long> statsMap = new java.util.HashMap<>();
        long totalLogins = 0;
        for (Object[] row : rawStats) {
            String dateStr = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            statsMap.put(dateStr, count);
            totalLogins += count;
        }

        List<String> dates = new java.util.ArrayList<>();
        List<Long> loginCounts = new java.util.ArrayList<>();

        for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dateStr = date.toString();
            dates.add(dateStr);
            loginCounts.add(statsMap.getOrDefault(dateStr, 0L));
        }

        return Map.of(
                "userId", userId,
                "fullName", user.getFullName(),
                "role", user.getRole().name(),
                "days", days,
                "totalLoginsInPeriod", totalLogins,
                "dates", dates,
                "activityTrend", loginCounts
        );
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