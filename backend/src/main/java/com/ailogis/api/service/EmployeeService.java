package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.*;
import com.ailogis.api.enums.*;
import com.ailogis.api.mapper.ContractMapper;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import com.ailogis.api.ws.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ContractMapper contractMapper;
    private final AiSubscriptionTierRepository aiTierRepository;
    private final SponsorTierRepository sponsorTierRepository;
    private final NotificationService notificationService;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

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
        notificationWebSocketHandler.broadcastWarehouseStatusChanged(updated.getId(), updated.getStatus().name());
        return warehouseMapper.toWarehouseResponseDTO(updated);
    }

    public StatisticResponseDTO getGlobalStatistics() {
        long totalUsers = userRepository.count();

        Map<String, Long> usersByRole = Map.of(
                "renter", userRepository.countByRole(Role.RENTER),
                "warehouse", userRepository.countByRole(Role.OWNER),
                "employee", userRepository.countByRole(Role.EMPLOYEE));

        Map<String, Long> warehousesByStatus = Map.of(
                "active", warehouseRepository.countByStatus(WarehouseStatus.ACTIVE),
                "rented", warehouseRepository.countByStatus(WarehouseStatus.RENTED),
                "pending", warehouseRepository.countByStatus(WarehouseStatus.PENDING),
                "rejected", warehouseRepository.countByStatus(WarehouseStatus.REJECTED),
                "inactive", warehouseRepository.countByStatus(WarehouseStatus.INACTIVE));

        Map<String, Long> rentRequestsByStatus = Map.of(
                "inprogress", rentalRequestRepository.countByStatus(RequestStatus.PENDING),
                "completed", rentalRequestRepository.countByStatus(RequestStatus.APPROVED),
                "cancelled", rentalRequestRepository.countByStatus(RequestStatus.REJECTED));

        Map<String, Long> contractsByStatus = Map.of(
                "active", contractRepository.countByStatus(ContractStatus.ACTIVE),
                "ended", contractRepository.countByStatus(ContractStatus.COMPLETED),
                "pending", contractRepository.countByStatus(ContractStatus.PENDING),
                "canceled", contractRepository.countByStatus(ContractStatus.CANCELED));

        return new StatisticResponseDTO(totalUsers, usersByRole, warehousesByStatus, rentRequestsByStatus,
                contractsByStatus);
    }

    public UserStatisticResponseDTO getUsersStatistics() {
        Map<String, Long> usersByRole = Map.of(
                "renter", userRepository.countByRole(Role.RENTER),
                "warehouse", userRepository.countByRole(Role.OWNER),
                "employee", userRepository.countByRole(Role.EMPLOYEE));
        return new UserStatisticResponseDTO(usersByRole);
    }

    public Page<UserDTO> searchUsers(String keyword, String roleStr, Pageable pageable) {
        Role roleEnum = null;
        if (roleStr != null && !roleStr.isBlank()) {
            try {
                roleEnum = Role.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Role tìm kiếm không hợp lệ!");
            }
        }

        return userRepository.searchUsers(keyword, roleEnum, pageable).map(u -> new UserDTO(
                u.getId(), u.getEmail(), u.getFullName(),
                u.getCompany() != null ? u.getCompany().getCompanyName() : "Cá nhân",
                u.getRole().name(), u.getStatus().name()));
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

        if (dto.name() != null)
            user.setFullName(dto.name());
        if (dto.phone() != null)
            user.setPhone(dto.phone());
        if (dto.status() != null)
            user.setStatus(com.ailogis.api.enums.UserStatus.valueOf(dto.status().toUpperCase()));
        if (dto.imgLink() != null)
            user.setAvatarUrl(dto.imgLink());
        if (dto.hashTaxCode() != null)
            user.setHashTaxCode(dto.hashTaxCode());

        User updated = userRepository.save(user);
        return new UserDTO(updated.getId(), updated.getEmail(), updated.getFullName(),
                updated.getCompany() != null ? updated.getCompany().getCompanyName() : null,
                updated.getRole().name(), updated.getStatus().name());
    }

    public Page<WarehouseEmployeeDTO> getWarehousesByStatus(WarehouseStatus status, Pageable pageable) {
        Page<Warehouse> warehouses = (status == null) ? warehouseRepository.findAll(pageable)
                : warehouseRepository.findByStatus(status, pageable);
        return warehouses.map(this::mapToEmployeeDTO);
    }

    @Transactional
    public WarehouseEmployeeDTO changeWarehouseStatus(Long warehouseId, WarehouseStatus newStatus) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

        warehouse.setStatus(newStatus);
        if (newStatus == WarehouseStatus.ACTIVE && isFullyRented(warehouse)) {
            warehouse.setStatus(WarehouseStatus.RENTED);
        }

        Warehouse saved = warehouseRepository.save(warehouse);

        if (newStatus == WarehouseStatus.ACTIVE) {
            notificationService.saveAndNotify(saved.getOwner().getId(),
                    "Kho '" + saved.getName() + "' đã được duyệt và đang hoạt động.");
        } else if (newStatus == WarehouseStatus.REJECTED) {
            notificationService.saveAndNotify(saved.getOwner().getId(),
                    "Kho '" + saved.getName() + "' đã bị từ chối bởi quản trị viên.");
        }

        notificationWebSocketHandler.broadcastWarehouseStatusChanged(saved.getId(), saved.getStatus().name());

        return mapToEmployeeDTO(saved);
    }

    public long getActiveUsersCount(int days) {
        if (days <= 0)
            days = 1; // Mặc định ít nhất là 1 ngày
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return userSessionRepository.countActiveUsersSince(since);
    }

    @Transactional
    public CertificationSubmitDTO reviewWarehouseCertification(Long submitId, CertReviewDTO dto) {
        CertificationSubmit submit = certificationSubmitRepository.findById(submitId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu yêu cầu chứng chỉ này!"));

        VerifyStatus newStatus = com.ailogis.api.enums.VerifyStatus.valueOf(dto.status().toUpperCase());
        submit.setStatus(newStatus);

        if (newStatus == com.ailogis.api.enums.VerifyStatus.REJECTED) {
            submit.setRejectReason(dto.rejectReason());
        } else if (newStatus == com.ailogis.api.enums.VerifyStatus.VERIFIED) {
            submit.setRejectReason(null);
            // Chỉ yêu cầu typeId khi duyệt thành công
            if (dto.typeId() != null) {
                CertificationType type = certificationTypeRepository.findById(dto.typeId())
                        .orElseThrow(() -> new RuntimeException("Loại chứng chỉ không tồn tại!"));
                submit.setType(type);
            }
        }

        CertificationSubmit saved = certificationSubmitRepository.save(submit);
        return new CertificationSubmitDTO(
                saved.getId(),
                saved.getType() != null ? saved.getType().getLabel() : "Chưa phân loại",
                saved.getLink(),
                saved.getStatus().name(),
                saved.getRejectReason());
    }

    @Transactional
    public UserDTO updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));
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

    private Map<String, Object> processTimeSeriesData(List<Object[]> rawStats, LocalDate start, LocalDate end,
            boolean isDaily) {
        List<String> labels = new java.util.ArrayList<>();
        // Sinh mảng labels (các ngày hoặc các giờ 0-23)
        if (isDaily) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
                labels.add(d.toString());
        } else {
            for (int i = 0; i < 24; i++)
                labels.add(String.valueOf(i));
        }

        Map<String, Map<String, Long>> roleDataMap = Map.of(
                Role.RENTER.name(), new java.util.HashMap<>(),
                Role.OWNER.name(), new java.util.HashMap<>(),
                Role.EMPLOYEE.name(), new java.util.HashMap<>());

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
        if (user.getRole() != Role.RENTER)
            throw new RuntimeException("User này không phải là RENTER!");

        CompanyResponseDTO companyDTO = user.getCompany() != null ? new CompanyResponseDTO(user.getCompany().getId(),
                user.getCompany().getCompanyName(), user.getCompany().getCompanyTaxCode()) : null;

        UserProfileDTO userProfile = new UserProfileDTO(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRole().name(), user.getStatus().name(),
                user.getDateOfBirth(), user.getGender() != null ? user.getGender().name() : null,
                companyDTO);

        String aiPlan = user.getAiTier() != null ? user.getAiTier().getLabel() : "Chưa đăng ký";

        // 1. Lấy Requests
        List<RentRequestResponseDTO> requests = rentalRequestRepository.findByRenterIdWithFilter(userId, null).stream()
                .map(r -> new RentRequestResponseDTO(
                        r.getId(),
                        r.getWarehouse().getId(),
                        r.getWarehouse().getName(),
                        r.getRenter() != null ? r.getRenter().getFullName() : "N/A",
                        r.getRenter() != null && r.getRenter().getCompany() != null
                                ? r.getRenter().getCompany().getCompanyName()
                                : null,
                        r.getRenter() != null && r.getRenter().getCompany() != null
                                ? r.getRenter().getCompany().getCompanyTaxCode()
                                : null,
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
                        r.getOwnerNote(),
                        r.getRenterNote(),
                        r.getRenter() != null ? r.getRenter().getPhone() : null,
                        r.getWarehouse().getOwner() != null ? r.getWarehouse().getOwner().getPhone() : null,
                        new java.util.ArrayList<RentRequestDetailResponseDTO>()))
                .toList();

        // 2. Lấy Contracts
        List<ContractResponseDTO> contracts = contractRepository.findAll().stream()
                .filter(c -> c.getRenter().getId().equals(userId))
                .map(contractMapper::toContractResponseDTO).toList();

        double totalSpending = 0;
        List<Transaction> transactions = transactionRepository.findByBuyerIdAndStatus(userId, "COMPLETED");
        for (Transaction t : transactions) {
            if (t.getSubscription() != null && t.getSubscription().getPrice() != null)
                totalSpending += t.getSubscription().getPrice();
            if (t.getSponsor() != null && t.getSponsor().getPricingPerMonth() != null)
                totalSpending += t.getSponsor().getPricingPerMonth();
        }

        return new RenterDetailResponseDTO(userProfile, aiPlan, requests, contracts, totalSpending);
    }

    // DETAIL OWNER
    @Transactional(readOnly = true)
    public OwnerDetailResponseDTO getOwnerDetail(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy User"));
        if (user.getRole() != Role.OWNER)
            throw new RuntimeException("User này không phải là OWNER!");

        CompanyResponseDTO companyDTO = user.getCompany() != null ? new CompanyResponseDTO(user.getCompany().getId(),
                user.getCompany().getCompanyName(), user.getCompany().getCompanyTaxCode()) : null;

        UserProfileDTO userProfile = new UserProfileDTO(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRole().name(), user.getStatus().name(),
                user.getDateOfBirth(), user.getGender() != null ? user.getGender().name() : null,
                companyDTO);

        List<WarehouseResponseDTO> warehouses = warehouseRepository.findByOwnerId(userId).stream()
                .map(warehouseMapper::toWarehouseResponseDTO).toList();

        List<RentRequestResponseDTO> requests = rentalRequestRepository.findByWarehouseOwnerIdWithFilter(userId, null)
                .stream()
                .map(r -> new RentRequestResponseDTO(
                        r.getId(),
                        r.getWarehouse().getId(),
                        r.getWarehouse().getName(),
                        r.getRenter() != null ? r.getRenter().getFullName() : "N/A",
                        r.getRenter() != null && r.getRenter().getCompany() != null
                                ? r.getRenter().getCompany().getCompanyName()
                                : null,
                        r.getRenter() != null && r.getRenter().getCompany() != null
                                ? r.getRenter().getCompany().getCompanyTaxCode()
                                : null,
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
                        r.getOwnerNote(),
                        r.getRenterNote(),
                        r.getRenter() != null ? r.getRenter().getPhone() : null,
                        r.getWarehouse().getOwner() != null ? r.getWarehouse().getOwner().getPhone() : null,
                        new java.util.ArrayList<RentRequestDetailResponseDTO>()))
                .toList();

        List<ContractResponseDTO> contracts = contractRepository.findAll().stream()
                .filter(c -> c.getOwner().getId().equals(userId))
                .map(contractMapper::toContractResponseDTO).toList();

        return new OwnerDetailResponseDTO(userProfile, warehouses, requests, contracts);
    }

    public Map<String, Object> getUserActivityStats(Long userId, int days) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (days <= 0)
            days = 7; // Mặc định 7 ngày
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
                "activityTrend", loginCounts);
    }

    // AI SUBSCRIPTION TIER
    public List<AiTierDTO> getAllAiTiers() {
        return aiTierRepository.findAll().stream().map(tier -> {
            long count = userRepository.countByAiTierId(tier.getId());
            return new AiTierDTO(tier.getId(), tier.getLabel(), tier.getDescription(),
                    tier.getTokenInput(), tier.getTokenOutput(), tier.getPrice(), tier.getUnit(), count);
        }).toList();
    }

    @Transactional
    public AiTierDTO createAiTier(AiTierDTO dto) {
        AiSubscriptionTier tier = AiSubscriptionTier.builder()
                .label(dto.label()).description(dto.description())
                .tokenInput(dto.tokenInput()).tokenOutput(dto.tokenOutput())
                .price(dto.price()).unit(dto.unit())
                .createdAt(LocalDate.now()).updatedAt(LocalDate.now())
                .build();
        AiSubscriptionTier saved = aiTierRepository.save(tier);
        return new AiTierDTO(saved.getId(), saved.getLabel(), saved.getDescription(), saved.getTokenInput(),
                saved.getTokenOutput(), saved.getPrice(), saved.getUnit(), 0L);
    }

    @Transactional
    public AiTierDTO updateAiTier(Long id, AiTierDTO dto) {
        AiSubscriptionTier tier = aiTierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói AI này!"));

        if (dto.label() != null)
            tier.setLabel(dto.label());
        if (dto.description() != null)
            tier.setDescription(dto.description());
        if (dto.tokenInput() != null)
            tier.setTokenInput(dto.tokenInput());
        if (dto.tokenOutput() != null)
            tier.setTokenOutput(dto.tokenOutput());
        if (dto.price() != null)
            tier.setPrice(dto.price());
        if (dto.unit() != null)
            tier.setUnit(dto.unit());
        tier.setUpdatedAt(LocalDate.now());

        AiSubscriptionTier updated = aiTierRepository.save(tier);
        long count = userRepository.countByAiTierId(id);
        return new AiTierDTO(updated.getId(), updated.getLabel(), updated.getDescription(), updated.getTokenInput(),
                updated.getTokenOutput(), updated.getPrice(), updated.getUnit(), count);
    }

    @Transactional
    public void deleteAiTier(Long id) {
        long count = userRepository.countByAiTierId(id);
        if (count > 0) {
            throw new RuntimeException("Không thể xóa! Đang có " + count + " người dùng sử dụng gói AI này.");
        }
        aiTierRepository.deleteById(id);
    }

    // SPONSOR TIER
    public List<SponsorTierDTO> getAllSponsorTiers() {
        return sponsorTierRepository.findAll().stream().map(tier -> {
            long count = warehouseRepository.countBySponsorTypeId(tier.getId());
            return new SponsorTierDTO(tier.getId(), tier.getPriorityLevel(), tier.getPricingPerMonth(),
                    tier.getYearPackSale(), tier.getLabel(), count, tier.getIsActive());
        }).toList();
    }

    @Transactional
    public SponsorTierDTO createSponsorTier(SponsorTierDTO dto) {
        SponsorTier tier = SponsorTier.builder()
                .label(dto.label()).priorityLevel(dto.priorityLevel())
                .pricingPerMonth(dto.pricingPerMonth()).yearPackSale(dto.yearPackSale())
                .updatedAt(LocalDate.now())
                .build();
        SponsorTier saved = sponsorTierRepository.save(tier);
        return new SponsorTierDTO(saved.getId(), saved.getPriorityLevel(), saved.getPricingPerMonth(),
                saved.getYearPackSale(), saved.getLabel(), 0L, tier.getIsActive());
    }

    @Transactional
    public SponsorTierDTO updateSponsorTier(Long id, SponsorTierDTO dto) {
        SponsorTier tier = sponsorTierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói Tài trợ này!"));

        if (dto.label() != null)
            tier.setLabel(dto.label());
        if (dto.priorityLevel() != null)
            tier.setPriorityLevel(dto.priorityLevel());
        if (dto.pricingPerMonth() != null)
            tier.setPricingPerMonth(dto.pricingPerMonth());
        if (dto.yearPackSale() != null)
            tier.setYearPackSale(dto.yearPackSale());
        tier.setUpdatedAt(LocalDate.now());

        SponsorTier updated = sponsorTierRepository.save(tier);
        long count = warehouseRepository.countBySponsorTypeId(id);
        return new SponsorTierDTO(updated.getId(), updated.getPriorityLevel(), updated.getPricingPerMonth(),
                updated.getYearPackSale(), updated.getLabel(), count, tier.getIsActive());
    }

    @Transactional
    public void deleteSponsorTier(Long id) {
        long count = warehouseRepository.countBySponsorTypeId(id);
        if (count > 0) {
            throw new RuntimeException("Không thể xóa! Đang có " + count + " kho bãi sử dụng gói Tài trợ này.");
        }
        sponsorTierRepository.deleteById(id);
    }

    // ==== EMPLOYEE TRANSACTION ANALYTICS ====

    @Transactional(readOnly = true)
    public Page<EmployeeTransactionDTO> searchAllTransactions(String type, String status, String buyerRoleStr,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Role buyerRole = null;
        if (buyerRoleStr != null && !buyerRoleStr.isBlank()) {
            try {
                buyerRole = Role.valueOf(buyerRoleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Vai trò tìm kiếm không hợp lệ!");
            }
        }

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

        return transactionRepository
                .searchAllTransactions(blankToNull(type), blankToNull(status), buyerRole, start, end, pageable)
                .map(this::mapToEmployeeTransactionDTO);
    }

    public TransactionAnalyticsSummaryDTO getTransactionAnalyticsSummary() {
        List<Object[]> typeRows = transactionRepository.sumAndCountByType();

        double grandTotal = 0;
        for (Object[] row : typeRows) {
            grandTotal += ((Number) row[1]).doubleValue();
        }

        List<TransactionTypeShareDTO> revenueByType = new java.util.ArrayList<>();
        String topServiceType = "";
        double topServiceRevenue = 0;
        String mostCommonType = "";
        long mostCommonTypeCount = 0;

        for (Object[] row : typeRows) {
            String type = (String) row[0];
            double totalAmount = ((Number) row[1]).doubleValue();
            long count = ((Number) row[2]).longValue();
            double percentage = grandTotal > 0 ? (totalAmount / grandTotal) * 100 : 0;
            revenueByType.add(new TransactionTypeShareDTO(type, totalAmount, count, percentage));

            if (totalAmount > topServiceRevenue) {
                topServiceRevenue = totalAmount;
                topServiceType = type;
            }
            if (count > mostCommonTypeCount) {
                mostCommonTypeCount = count;
                mostCommonType = type;
            }
        }

        List<Object[]> roleRows = transactionRepository.sumAmountByBuyerRole();
        String topSpendingRole = "";
        double topSpendingRoleAmount = 0;
        for (Object[] row : roleRows) {
            Role role = (Role) row[0];
            double totalAmount = row[1] != null ? ((Number) row[1]).doubleValue() : 0;
            if (totalAmount > topSpendingRoleAmount) {
                topSpendingRoleAmount = totalAmount;
                topSpendingRole = role != null ? role.name() : "";
            }
        }

        HighestTransactionDTO highestTransaction = transactionRepository
                .findTopByAmountDesc(org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst()
                .map(t -> new HighestTransactionDTO(
                        t.getId(), t.getAmount(), t.getType(), t.getStatus(),
                        t.getBuyer().getId(), t.getBuyer().getFullName(), t.getBuyer().getRole().name(),
                        t.getCreatedAt()))
                .orElse(null);

        return new TransactionAnalyticsSummaryDTO(revenueByType, topServiceType, topServiceRevenue,
                mostCommonType, mostCommonTypeCount, highestTransaction, topSpendingRole, topSpendingRoleAmount);
    }

    public List<RevenuePointDTO> getTransactionRevenueTimeseries(String granularity, LocalDate startDate,
            LocalDate endDate) {
        if (endDate.isAfter(LocalDate.now())) {
            throw new RuntimeException("Ngày kết thúc không được ở tương lai!");
        }
        if (startDate.isAfter(endDate)) {
            throw new RuntimeException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc!");
        }

        String gran = granularity != null ? granularity.toLowerCase() : "";
        if (!gran.equals("day") && !gran.equals("month") && !gran.equals("year")) {
            throw new RuntimeException("Đơn vị thời gian không hợp lệ! Chỉ chấp nhận: day, month, year.");
        }

        switch (gran) {
            case "day" -> {
                if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 730) {
                    throw new RuntimeException("Khoảng thời gian quá lớn cho đơn vị 'day'. Tối đa: 730 ngày.");
                }
            }
            case "month" -> {
                if (java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate) > 120) {
                    throw new RuntimeException("Khoảng thời gian quá lớn cho đơn vị 'month'. Tối đa: 120 tháng.");
                }
            }
            case "year" -> {
                if (java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate) > 50) {
                    throw new RuntimeException("Khoảng thời gian quá lớn cho đơn vị 'year'. Tối đa: 50 năm.");
                }
            }
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Object[]> rawStats = transactionRepository.sumRevenueByGranularityAndRole(gran, startDateTime,
                endDateTime);

        // label -> [renterAmount, ownerAmount, totalAmount] — totalAmount accumulates
        // across every buyer role (RENTER/OWNER/and any other), so "both" always
        // reflects true total revenue even if a non-renter/owner role ever buys.
        Map<String, double[]> dataMap = new java.util.HashMap<>();
        java.time.format.DateTimeFormatter dayFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        java.time.format.DateTimeFormatter monthFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM");
        java.time.format.DateTimeFormatter yearFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy");

        for (Object[] row : rawStats) {
            LocalDateTime bucketStart = toLocalDateTime(row[0]);
            String buyerRole = row[1] != null ? row[1].toString() : null;
            double amount = row[2] != null ? ((Number) row[2]).doubleValue() : 0;

            String label = switch (gran) {
                case "day" -> bucketStart.format(dayFmt);
                case "month" -> bucketStart.format(monthFmt);
                default -> bucketStart.format(yearFmt);
            };
            double[] vals = dataMap.computeIfAbsent(label, k -> new double[3]);
            if ("RENTER".equals(buyerRole)) {
                vals[0] += amount;
            } else if ("OWNER".equals(buyerRole)) {
                vals[1] += amount;
            }
            vals[2] += amount;
        }

        List<RevenuePointDTO> result = new java.util.ArrayList<>();
        switch (gran) {
            case "day" -> {
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    String label = d.format(dayFmt);
                    double[] vals = dataMap.getOrDefault(label, new double[3]);
                    result.add(new RevenuePointDTO(label, d.atStartOfDay(), vals[0], vals[1], vals[2]));
                }
            }
            case "month" -> {
                java.time.YearMonth startYm = java.time.YearMonth.from(startDate);
                java.time.YearMonth endYm = java.time.YearMonth.from(endDate);
                for (java.time.YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
                    String label = ym.format(monthFmt);
                    double[] vals = dataMap.getOrDefault(label, new double[3]);
                    result.add(new RevenuePointDTO(label, ym.atDay(1).atStartOfDay(), vals[0], vals[1], vals[2]));
                }
            }
            default -> {
                for (int y = startDate.getYear(); y <= endDate.getYear(); y++) {
                    String label = String.valueOf(y);
                    double[] vals = dataMap.getOrDefault(label, new double[3]);
                    result.add(new RevenuePointDTO(label, LocalDate.of(y, 1, 1).atStartOfDay(), vals[0], vals[1],
                            vals[2]));
                }
            }
        }

        return result;
    }

    /**
     * Soft-deletes a transaction by marking its status as DELETED rather than
     * removing the row. Every query behind the transaction analytics page
     * (searchAllTransactions, sumAndCountByType, sumAmountByBuyerRole,
     * findTopByAmountDesc, sumRevenueByGranularityAndRole) filters out
     * status = 'DELETED', so the row disappears from that page's table and every
     * metric. Overwriting status also means other status-specific consumers
     * (AI/sponsor active-subscription checks, the abandoned-PENDING cleanup job,
     * etc.) stop recognizing this transaction, since none of them match 'DELETED' —
     * an accepted side effect of reusing the status field instead of adding a
     * dedicated flag.
     */
    @Transactional
    public void softDeleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch!"));
        transaction.setStatus("DELETED");
        transactionRepository.save(transaction);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.time.Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        throw new RuntimeException("Không thể đọc dữ liệu thời gian từ cơ sở dữ liệu!");
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private EmployeeTransactionDTO mapToEmployeeTransactionDTO(Transaction tx) {
        String description = "Giao dịch hệ thống";

        if ("SPONSOR_SUBSCRIPTION".equals(tx.getType()) && tx.getSponsor() != null) {
            description = "Gói Sponsor: " + tx.getSponsor().getLabel();
        } else if ("AI_SUBSCRIPTION".equals(tx.getType()) && tx.getSubscription() != null) {
            description = "Gói AI: " + tx.getSubscription().getLabel();
        } else if ("RENTAL_FEE".equals(tx.getType()) && tx.getRentalRequest() != null) {
            description = "Phí liên hệ kho: " + tx.getRentalRequest().getWarehouse().getName();
        }

        User buyer = tx.getBuyer();
        return new EmployeeTransactionDTO(
                tx.getId(),
                buyer != null ? buyer.getId() : null,
                buyer != null ? buyer.getFullName() : null,
                buyer != null ? buyer.getEmail() : null,
                buyer != null && buyer.getRole() != null ? buyer.getRole().name() : null,
                tx.getType(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getCreatedAt(),
                tx.getInvoiceDate(),
                description);
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
                if (s.getTempMin() != null && s.getTempMin() < tempMin)
                    tempMin = s.getTempMin();
                if (s.getTempMax() != null && s.getTempMax() > tempMax)
                    tempMax = s.getTempMax();

                sections.add(Map.of(
                        "id_section", s.getId(),
                        "name", s.getLabel() != null ? s.getLabel() : "Khu " + s.getSector(),
                        "temp_min", s.getTempMin(),
                        "temp_max", s.getTempMax(),
                        "total_capacity", s.getTotalCapacity()));
            }
        }

        if (tempMin == Double.MAX_VALUE)
            tempMin = 0.0;
        if (tempMax == Double.MIN_VALUE)
            tempMax = 0.0;

        Map<String, Object> stats = Map.of(
                "totalCapacity", totalCap,
                "availableCapacity", availableCap,
                "temperatureMin", tempMin,
                "temperatureMax", tempMax,
                "securityLevel", "high" // Hardcode tạm thời theo JSON mẫu
        );

        List<CertificationSubmitDTO> certs = w.getCertificationSubmits() != null ? w.getCertificationSubmits().stream()
                .map(c -> new CertificationSubmitDTO(
                        c.getId(),
                        c.getType() != null ? c.getType().getLabel() : "Chưa phân loại",
                        c.getLink(),
                        c.getStatus() != null ? c.getStatus().name() : "PENDING",
                        c.getRejectReason()))
                .toList() : List.of();

        return new WarehouseEmployeeDTO(
                w.getId(), w.getOwner().getId(), w.getName(), w.getLocationAddressText(),
                w.getLocationCommune(), w.getLocationProvince(),
                w.getStatus().name().toLowerCase(),
                w.getOwner().getCompany() != null ? w.getOwner().getCompany().getCompanyName()
                        : w.getOwner().getFullName(),
                minPrice, stats, sections, certs);
    }

    private boolean isFullyRented(Warehouse w) {
        if (w.getSections() == null || w.getSections().isEmpty())
            return false;
        return w.getSections().stream()
                .allMatch(s -> s.getAvailableCapacity() != null && s.getAvailableCapacity() <= 0);
    }
}