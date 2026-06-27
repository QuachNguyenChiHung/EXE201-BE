package com.ailogis.api.service;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.Review;
import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.entity.WarehouseView;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.WarehouseStatus;
import com.ailogis.api.mapper.WarehouseMapper;
import com.ailogis.api.repository.*;
import com.ailogis.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WarehouseService {

        private final WarehouseRepository warehouseRepository;
        private final WarehouseViewRepository warehouseViewRepository;
        private final WarehouseMapper warehouseMapper;
        private final SponsorTierRepository sponsorTierRepository;
        private final CertificationTypeRepository certificationTypeRepository;
        private final ReviewRepository reviewRepository;
        private final WarehouseSectionRepository warehouseSectionRepository;
        private final PriceTierRepository priceTierRepository;

        public Page<WarehouseResponseDTO> getActiveOnlyWarehouses(Pageable pageable) {
                return warehouseRepository.findByStatus(WarehouseStatus.ACTIVE, pageable)
                                .map(warehouseMapper::toWarehouseResponseDTO);
        }

        // DÀNH CHO OWNER/EMPLOYEE: Chỉ xem dữ liệu chi tiết thô, KHÔNG ghi nhận log
        // lượt xem mới
        public WarehouseResponseDTO getWarehouseById(Long id) {
                Warehouse warehouse = warehouseRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));
                return warehouseMapper.toWarehouseResponseDTO(warehouse);
        }

        /**
         * Fetch a batch of warehouses by their IDs and map them to DTOs.
         * The returned list preserves the order of the input {@code ids} list
         * (useful when the AI returns IDs ranked by relevance).
         * IDs that do not exist in the DB are silently skipped.
         *
         * @param ids ordered list of warehouse IDs
         * @return DTOs in the same order as {@code ids}
         */
        public List<WarehouseResponseDTO> getWarehousesByIds(List<Long> ids) {
                if (ids == null || ids.isEmpty()) return List.of();
                // findAllById returns in arbitrary order — index by id to restore rank order
                Map<Long, WarehouseResponseDTO> byId = new HashMap<>();
                warehouseRepository.findAllById(ids)
                                .forEach(w -> byId.put(w.getId(), warehouseMapper.toWarehouseResponseDTO(w)));
                return ids.stream()
                                .filter(byId::containsKey)
                                .map(byId::get)
                                .collect(java.util.stream.Collectors.toList());
        }

        // DÀNH CHO PUBLIC/RENTER: Xem chi tiết + Ghi nhận log lượt xem
        @Transactional
        public WarehouseResponseDTO getWarehouseDetailWithViewTracking(Long id, CustomUserDetails userDetails) {
                Warehouse warehouse = warehouseRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

                // Chỉ tạo log view nếu là Renter (người dùng đăng nhập)
                if (userDetails != null && userDetails.getUser().getRole() == Role.RENTER) {
                        WarehouseView viewLog = WarehouseView.builder()
                                        .renter(userDetails.getUser())
                                        .warehouse(warehouse)
                                        .viewDate(LocalDate.now())
                                        .build();
                        warehouseViewRepository.save(viewLog);
                }
                return warehouseMapper.toWarehouseResponseDTO(warehouse);
        }

        public Map<String, Object> getWarehouseViewStats(Long warehouseId, int days, CustomUserDetails userDetails) {
                Warehouse warehouse = warehouseRepository.findById(warehouseId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

                Role role = userDetails.getUser().getRole();
                if (role != Role.EMPLOYEE && !warehouse.getOwner().getId().equals(userDetails.getUser().getId())) {
                        throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền xem thống kê của kho bãi này!");
                }

                if (days <= 0)
                        days = 7;
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusDays(days - 1);

                List<Object[]> rawStats = warehouseViewRepository.countViewsByDateForWarehouse(warehouseId, startDate);

                Map<String, Long> statsMap = new HashMap<>();
                long totalViews = 0;
                for (Object[] row : rawStats) {
                        String dateStr = (String) row[0];
                        Long count = ((Number) row[1]).longValue();
                        statsMap.put(dateStr, count);
                        totalViews += count;
                }

                List<String> dates = new ArrayList<>();
                List<Long> viewTrend = new ArrayList<>();

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                        String dateStr = date.toString();
                        dates.add(dateStr);
                        viewTrend.add(statsMap.getOrDefault(dateStr, 0L));
                }

                return Map.of(
                                "warehouseId", warehouseId,
                                "warehouseName", warehouse.getName(),
                                "days", days,
                                "totalViewsInPeriod", totalViews,
                                "dates", dates,
                                "viewTrend", viewTrend);
        }

        public WarehouseLocationDTO getWarehouseLocation(Long id) {
                Warehouse warehouse = warehouseRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho bãi!"));

                return new WarehouseLocationDTO(
                                warehouse.getLocationLong(),
                                warehouse.getLocationLat());
        }

        public Page<WarehouseResponseDTO> getPopularWarehouses(Pageable pageable) {
                LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
                return warehouseRepository.findPopularWarehouses(thirtyDaysAgo, pageable)
                                .map(warehouseMapper::toWarehouseResponseDTO);
        }

        public Page<WarehouseResponseDTO> searchWarehouses(
                        String keyword, List<String> provinces, Double minArea, Double maxArea,
                        Double minPrice, Double maxPrice, Double minRating, Double maxRating, List<Long> certTypeIds,
                        Pageable pageable) {

                String safeKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();
                List<String> safeProvinces = (provinces == null) ? List.of() : provinces;
                List<Long> safeCertTypeIds = (certTypeIds == null) ? List.of() : certTypeIds;

                boolean hasProvinces = !safeProvinces.isEmpty();
                boolean hasCerts = !safeCertTypeIds.isEmpty();
                // Pass a non-empty dummy list when skipping — empty IN() is invalid SQL.
                List<String> provincesParam = hasProvinces ? safeProvinces : List.of("__none__");
                List<Long> certsParam = hasCerts ? safeCertTypeIds : List.of(-1L);

                return warehouseRepository
                                .searchWarehouses(safeKeyword, hasProvinces, provincesParam,
                                                minArea, maxArea, minPrice, maxPrice,
                                                minRating, maxRating, hasCerts, certsParam, pageable)
                                .map(warehouseMapper::toWarehouseResponseDTO);
        }

        public Page<WarehouseResponseDTO> searchWarehousesByCriteria(SearchCriteriaDTO criteria, Pageable pageable) {
                List<String> provinces = new ArrayList<>();
                if (criteria.location() != null) {
                        for (SearchCriteriaDTO.LocationDTO loc : criteria.location()) {
                                String normalized = normalizeProvince(loc.province());
                                if (normalized != null && !normalized.isBlank()) {
                                        provinces.add(normalized);
                                }
                        }
                }

                Double minAvail = criteria.availableCapacity() != null ? criteria.availableCapacity().min_range() : null;
                Double maxAvail = criteria.availableCapacity() != null ? criteria.availableCapacity().max_range() : null;
                Double minTotal = criteria.totalCapacity() != null ? criteria.totalCapacity().min_range() : null;
                Double maxTotal = criteria.totalCapacity() != null ? criteria.totalCapacity().max_range() : null;
                String sortType = criteria.sort() != null ? criteria.sort().type() : null;

                // Pass empty string (not null) for keyword so JPQL doesn't generate
                // `lower(bytea)` errors
                String safeKeyword = (criteria.name() == null || criteria.name().isBlank()) ? "" : criteria.name().trim();
                
                boolean hasProvinces = !provinces.isEmpty();
                List<String> provincesParam = hasProvinces ? provinces : List.of("__none__");

                return warehouseRepository.searchWarehousesByCriteria(
                                safeKeyword, hasProvinces, provincesParam,
                                criteria.tempMin(), criteria.tempMax(),
                                minAvail, maxAvail, minTotal, maxTotal,
                                criteria.minPrice(), criteria.maxPrice(),
                                criteria.rating() != null ? criteria.rating().min_range() : null,
                                criteria.rating() != null ? criteria.rating().max_range() : null,
                                sortType, pageable).map(warehouseMapper::toWarehouseResponseDTO);
        }

        /**
         * Normalize Vietnamese province aliases to the canonical form used in the DB.
         * The AI may extract "TP.HCM", "TPHCM", "HCMC", "tp hcm" etc. — but the DB
         * stores
         * the canonical Vietnamese names ("Hồ Chí Minh", "Hà Nội", ...). This map
         * ensures
         * any common alias resolves to the canonical form so the exact-match JPQL
         * works.
         */
        private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
                        Map.entry("tp.hcm", "Hồ Chí Minh"),
                        Map.entry("tp hcm", "Hồ Chí Minh"),
                        Map.entry("tphcm", "Hồ Chí Minh"),
                        Map.entry("hcmc", "Hồ Chí Minh"),
                        Map.entry("ho chi minh", "Hồ Chí Minh"),
                        Map.entry("ho chi minh city", "Hồ Chí Minh"),
                        Map.entry("thành phố hồ chí minh", "Hồ Chí Minh"),
                        Map.entry("sài gòn", "Hồ Chí Minh"),
                        Map.entry("saigon", "Hồ Chí Minh"),
                        Map.entry("tp.hà nội", "Hà Nội"),
                        Map.entry("tp hà nội", "Hà Nội"),
                        Map.entry("tphn", "Hà Nội"),
                        Map.entry("hn", "Hà Nội"),
                        Map.entry("hanoi", "Hà Nội"),
                        Map.entry("đà nẵng", "Đà Nẵng"),
                        Map.entry("da nang", "Đà Nẵng"),
                        Map.entry("danang", "Đà Nẵng"),
                        Map.entry("hải phòng", "Hải Phòng"),
                        Map.entry("hai phong", "Hải Phòng"),
                        Map.entry("cần thơ", "Cần Thơ"),
                        Map.entry("can tho", "Cần Thơ"));

        /**
         * Look up a Vietnamese province name in the alias map.
         * Returns the canonical DB form if found, otherwise the trimmed input
         * unchanged.
         */
        static String normalizeProvince(String raw) {
                if (raw == null)
                        return null;
                String trimmed = raw.trim();
                if (trimmed.isEmpty())
                        return trimmed;
                String key = trimmed.toLowerCase();
                return PROVINCE_ALIASES.getOrDefault(key, trimmed);
        }

        public AiFilterMetaResponseDTO getAiFilterMeta() {
                List<String> provinces = warehouseRepository.findDistinctProvinces();

                List<AiFilterMetaResponseDTO.CertInfo> certs = certificationTypeRepository.findAll().stream()
                                .map(c -> new AiFilterMetaResponseDTO.CertInfo(c.getId().toString(), c.getLabel(), c.getDescription()))
                                .toList();

                List<Object[]> tempResult = warehouseSectionRepository.findTemperatureRange();
                List<Object[]> capResult = warehouseSectionRepository.findCapacityRange();
                List<Object[]> priceResult = priceTierRepository.findActivePriceRange();

                return new AiFilterMetaResponseDTO(
                                provinces, certs,
                                extractRange(tempResult, 0, -30.0),
                                extractRange(tempResult, 1, 15.0),
                                extractRange(capResult, 0, 0.0),
                                extractRange(capResult, 1, 10000.0),
                                extractRange(priceResult, 0, 0.0),
                                extractRange(priceResult, 1, 1000000.0));
        }

        private static Double extractRange(List<Object[]> rows, int col, double fallback) {
                if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0)[col] == null)
                        return fallback;
                return ((Number) rows.get(0)[col]).doubleValue();
        }

        public FilterMetaResponseDTO getFilterMeta() {
                // 1. Lấy danh sách Địa điểm thực tế đang có kho bãi
                List<String> locations = warehouseRepository.findDistinctProvinces();

                // 2. Trạng thái mà khách thuê được phép tìm kiếm
                List<String> statuses = List.of(
                                WarehouseStatus.ACTIVE.name(),
                                WarehouseStatus.RENTED.name());

                // 3. Danh sách các gói Sponsor để Frontend vẽ nút
                List<SponsorTierDTO> sponsorTiers = sponsorTierRepository.findByIsActiveTrue().stream()
                                .map(t -> new SponsorTierDTO(t.getId(), t.getPriorityLevel(), t.getPricingPerMonth(),
                                                t.getYearPackSale(), t.getLabel(), null, t.getIsActive()))
                                .toList();

                // 4. Danh sách các Chứng chỉ (HACCP, ISO...)
                List<CertDTO> certifications = certificationTypeRepository.findAll().stream()
                                .map(c -> new CertDTO(c.getId().toString(), c.getLabel(), c.getLawReferences(),
                                                c.getUpdateDate() != null ? c.getUpdateDate().toString() : null,
                                                c.getPdfLink()))
                                .toList();

                return new FilterMetaResponseDTO(locations, statuses, sponsorTiers, certifications);
        }

        public List<ReviewResponseDTO> getWarehouseReviews(Long warehouseId) {
                List<Review> reviews = reviewRepository.findByWarehouseId(warehouseId);

                return reviews.stream().map(r -> new ReviewResponseDTO(
                                r.getId(),
                                r.getUser() != null ? r.getUser().getFullName() : "Khách hàng ẩn danh",
                                r.getRating(),
                                r.getComment())).toList();
        }
}