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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
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
                                                minRating, maxRating, hasCerts, certsParam, null, pageable)
                                .map(warehouseMapper::toWarehouseResponseDTO);
        }

        public Page<WarehouseResponseDTO> searchWarehousesByCriteria(SearchCriteriaDTO criteria, Pageable pageable) {
                log.info("[WarehouseService/searchWarehousesByCriteria] priceTier='{}' minPrice={} maxPrice={}",
                        criteria.priceTier(), criteria.minPrice(), criteria.maxPrice());

                List<String> provinces = new ArrayList<>();
                boolean hasNoWarehouseSentinel = false;
                if (criteria.location() != null) {
                        for (SearchCriteriaDTO.LocationDTO loc : criteria.location()) {
                                String normalized = normalizeProvince(loc.province());
                                if (normalized == null || normalized.isBlank())
                                        continue;
                                if ("__no_warehouse__".equals(normalized)) {
                                        hasNoWarehouseSentinel = true;
                                        continue;
                                }
                                provinces.add(normalized);
                        }
                }

                // If user asked for a city with no warehouse coverage, return empty immediately
                if (hasNoWarehouseSentinel && provinces.isEmpty()) {
                        return new PageImpl<>(List.of(), pageable, 0);
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

                // Map certificate labels to IDs
                List<Long> certTypeIds = new ArrayList<>();
                if (criteria.certificates() != null && !criteria.certificates().isEmpty()) {
                        List<com.ailogis.api.entity.CertificationType> dbCerts = certificationTypeRepository.findAll();
                        for (String certLabel : criteria.certificates()) {
                                dbCerts.stream()
                                        .filter(c -> c.getLabel().equalsIgnoreCase(certLabel))
                                        .findFirst()
                                        .ifPresent(c -> certTypeIds.add(c.getId()));
                        }
                }
                boolean hasCerts = !certTypeIds.isEmpty();
                List<Long> certsParam = hasCerts ? certTypeIds : List.of(-1L);

                return warehouseRepository.searchWarehousesByCriteria(
                                safeKeyword, hasProvinces, provincesParam,
                                criteria.tempMin(), criteria.tempMax(),
                                minAvail, maxAvail, minTotal, maxTotal,
                                criteria.minPrice(), criteria.maxPrice(),
                                criteria.rating() != null ? criteria.rating().min_range() : null,
                                criteria.rating() != null ? criteria.rating().max_range() : null,
                                hasCerts, certsParam,
                                sortType, criteria.priceTier(), pageable).map(warehouseMapper::toWarehouseResponseDTO);
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
			// ── Ho Chi Minh City ──────────────────────────────────────────────────
			Map.entry("tp.hcm",                      "Thành phố Hồ Chí Minh"),
			Map.entry("tp hcm",                      "Thành phố Hồ Chí Minh"),
			Map.entry("tphcm",                       "Thành phố Hồ Chí Minh"),
			Map.entry("hcmc",                        "Thành phố Hồ Chí Minh"),
			Map.entry("ho chi minh",                 "Thành phố Hồ Chí Minh"),
			Map.entry("ho chi minh city",            "Thành phố Hồ Chí Minh"),
			Map.entry("thành phố hồ chí minh",       "Thành phố Hồ Chí Minh"),
			Map.entry("sài gòn",                     "Thành phố Hồ Chí Minh"),
			Map.entry("saigon",                      "Thành phố Hồ Chí Minh"),
			// Đông Nam Bộ — Bà Rịa Vũng Tàu merged into HCM coverage zone
			Map.entry("bà rịa vũng tàu",            "Thành phố Hồ Chí Minh"),
			Map.entry("ba ria vung tau",             "Thành phố Hồ Chí Minh"),
			Map.entry("tỉnh bà rịa - vũng tàu",     "Thành phố Hồ Chí Minh"),
			Map.entry("tinh ba ria - vung tau",      "Thành phố Hồ Chí Minh"),
			Map.entry("vũng tàu",                   "Thành phố Hồ Chí Minh"),
			Map.entry("vung tau",                    "Thành phố Hồ Chí Minh"),
			Map.entry("bà rịa",                     "Thành phố Hồ Chí Minh"),
			Map.entry("ba ria",                      "Thành phố Hồ Chí Minh"),
			// ── Ha Noi ─────────────────────────────────────────────────────────
			Map.entry("tp.hà nội",                   "Thành phố Hà Nội"),
			Map.entry("tp hà nội",                   "Thành phố Hà Nội"),
			Map.entry("tp hanoi",                    "Thành phố Hà Nội"),
			Map.entry("tphn",                        "Thành phố Hà Nội"),
			Map.entry("hn",                           "Thành phố Hà Nội"),
			Map.entry("hanoi",                       "Thành phố Hà Nội"),
			// ── Da Nang ────────────────────────────────────────────────────────
			Map.entry("đà nẵng",                    "Thành phố Đà Nẵng"),
			Map.entry("da nang",                     "Thành phố Đà Nẵng"),
			Map.entry("danang",                      "Thành phố Đà Nẵng"),
			// ── Hai Phong ──────────────────────────────────────────────────────
			Map.entry("hải phòng",                  "Thành phố Hải Phòng"),
			Map.entry("hai phong",                   "Thành phố Hải Phòng"),
			// ── Can Tho ────────────────────────────────────────────────────────
			Map.entry("cần thơ",                     "Thành phố Cần Thơ"),
			Map.entry("can tho",                      "Thành phố Cần Thơ"),
			// ── Binh Duong ─────────────────────────────────────────────────────
			Map.entry("bình dương",                  "Tỉnh Bình Dương"),
			Map.entry("binh duong",                  "Tỉnh Bình Dương"),
			// ── Dong Nai ──────────────────────────────────────────────────────
			Map.entry("đồng nai",                    "Tỉnh Đồng Nai"),
			Map.entry("dong nai",                    "Tỉnh Đồng Nai"),
			// ── Long An ────────────────────────────────────────────────────────
			Map.entry("tỉnh long an",                "Tỉnh Long An"),
			Map.entry("tinh long an",                "Tỉnh Long An"),
			// ── Cities WITHOUT warehouses in the DB — sentinel so callers detect out-of-coverage
			Map.entry("nha trang",                   "__no_warehouse__"),
			Map.entry("nhatrang",                    "__no_warehouse__"),
			Map.entry("khánh hòa",                  "__no_warehouse__"),
			Map.entry("khanh hoa",                  "__no_warehouse__"),
			Map.entry("huế",                        "__no_warehouse__"),
			Map.entry("hue",                         "__no_warehouse__"),
			Map.entry("thừa thiên huế",             "__no_warehouse__"),
			Map.entry("thua thien hue",              "__no_warehouse__"),
			Map.entry("thanh hóa",                  "__no_warehouse__"),
			Map.entry("thanhhoa",                   "__no_warehouse__"),
			Map.entry("lâm đồng",                   "__no_warehouse__"),
			Map.entry("lam dong",                   "__no_warehouse__"),
			Map.entry("cà mau",                      "__no_warehouse__"),
			Map.entry("ca mau",                      "__no_warehouse__"),
			Map.entry("vĩnh long",                  "__no_warehouse__"),
			Map.entry("vinh long",                  "__no_warehouse__"),
			Map.entry("quảng ninh",                 "__no_warehouse__"),
			Map.entry("quangninh",                  "__no_warehouse__"),
			Map.entry("bình thuận",                 "__no_warehouse__"),
			Map.entry("binh thuan",                 "__no_warehouse__"),
			Map.entry("bắc ninh",                   "__no_warehouse__"),
			Map.entry("bac ninh",                   "__no_warehouse__"),
			Map.entry("hưng yên",                   "__no_warehouse__"),
			Map.entry("hung yen",                   "__no_warehouse__"),
			Map.entry("hải dương",                  "__no_warehouse__"),
			Map.entry("hai duong",                  "__no_warehouse__"),
			Map.entry("nam định",                   "__no_warehouse__"),
			Map.entry("nam dinh",                   "__no_warehouse__"),
			Map.entry("thái bình",                  "__no_warehouse__"),
			Map.entry("thai binh",                  "__no_warehouse__"),
			Map.entry("ninh bình",                  "__no_warehouse__"),
			Map.entry("ninh binh",                  "__no_warehouse__"),
			Map.entry("hà nam",                     "__no_warehouse__"),
			Map.entry("ha nam",                     "__no_warehouse__"),
			Map.entry("quảng nam",                  "__no_warehouse__"),
			Map.entry("quang nam",                  "__no_warehouse__"),
			Map.entry("quảng ngãi",                 "__no_warehouse__"),
			Map.entry("quang ngai",                 "__no_warehouse__"),
			Map.entry("bình định",                 "__no_warehouse__"),
			Map.entry("binh dinh",                 "__no_warehouse__"),
			Map.entry("phú yên",                   "__no_warehouse__"),
			Map.entry("phu yen",                   "__no_warehouse__"),
			Map.entry("bình phước",                 "__no_warehouse__"),
			Map.entry("binh phuoc",                 "__no_warehouse__"),
			Map.entry("tây ninh",                  "__no_warehouse__"),
			Map.entry("tay nin",                   "__no_warehouse__"),
			Map.entry("tiền giang",                 "__no_warehouse__"),
			Map.entry("tien giang",                 "__no_warehouse__"),
			Map.entry("bến tre",                    "__no_warehouse__"),
			Map.entry("ben tre",                    "__no_warehouse__"),
			Map.entry("trà vinh",                  "__no_warehouse__"),
			Map.entry("tra vinh",                  "__no_warehouse__"),
			Map.entry("hậu giang",                  "__no_warehouse__"),
			Map.entry("hau giang",                  "__no_warehouse__"),
			Map.entry("sóc trăng",                  "__no_warehouse__"),
			Map.entry("soc trang",                  "__no_warehouse__"),
			Map.entry("an giang",                  "__no_warehouse__"),
			Map.entry("kiên giang",                 "__no_warehouse__"),
			Map.entry("kien giang",                 "__no_warehouse__"),
			Map.entry("đồng tháp",                 "__no_warehouse__"),
			Map.entry("dong thap",                  "__no_warehouse__"));

	// Must exactly match the values stored in the DB (locationProvince column).
	// Stored values confirmed from DataInitializer.java.
	private static final Set<String> VALID_PROVINCES = Set.of(
			"Thành phố Hồ Chí Minh",
			"Tỉnh Bình Dương",
			"Tỉnh Đồng Nai",
			"Tỉnh Long An",
			"Tỉnh Bà Rịa - Vũng Tàu",
			"Thành phố Cần Thơ",
			"Thành phố Hà Nội",
			"Thành phố Hải Phòng",
			"Thành phố Đà Nẵng");

	/**
	 * Look up a Vietnamese province name in the alias map.
	 * Returns the canonical DB form if found, otherwise the trimmed input unchanged.
	 * Returns "__no_warehouse__" for cities without warehouse coverage so callers
	 * can detect out-of-coverage requests.
	 */
	static String normalizeProvince(String raw) {
		if (raw == null)
			return null;
		String trimmed = raw.trim();
		if (trimmed.isEmpty())
			return trimmed;
		String key = trimmed.toLowerCase();
		String resolved = PROVINCE_ALIASES.getOrDefault(key, trimmed);
		// __no_warehouse__ sentinel means the city has no coverage
		if ("__no_warehouse__".equals(resolved))
			return "__no_warehouse__";
		// Always validate resolved value against VALID_PROVINCES (case-insensitive).
		// This ensures aliases resolve to the exact DB form (e.g. "Thành phố Hồ Chí Minh"
		// not "Hồ Chí Minh") and raw input gets canonicalised.
		return VALID_PROVINCES.stream()
				.filter(p -> p.equalsIgnoreCase(resolved))
				.findFirst()
				.orElse(resolved);
	}

	/**
	 * Vietnamese tokens that strongly imply the user is referring to a location,
	 * even without naming a specific province.
	 */
	private static final Set<String> LOCATION_HINT_TOKENS = Set.of(
			"ở", "tại", "khu vực", "vùng", "miền", "tỉnh", "thành phố",
			"tp.", "tp ", "city", "province", "region", "khu vuc", "tinh", "thanh pho",
			"gần", "quanh", "khoảng");

	/**
	 * Returns true when the user query appears to mention a specific location.
	 * Detection layers (any one is enough):
	 *  1. Any Vietnamese location-hint token ("ở", "tại", "miền Bắc", ...).
	 *  2. Any province-alias key (e.g. "hcm", "tphcm", "ha noi") appears in the query.
	 *  3. Any canonical province name (e.g. "Hồ Chí Minh", "Cần Thơ") appears in the query.
	 *
	 * Used by the AI layer as a server-side backstop: if the user did not mention
	 * a location but the AI agent hallucinated one in the criteria JSON, we clear
	 * the location list before querying the DB.
	 */
	public static boolean queryMentionsLocation(String query) {
		if (query == null || query.isBlank())
			return false;
		String q = query.toLowerCase();

		for (String token : LOCATION_HINT_TOKENS) {
			if (q.contains(token))
				return true;
		}
		for (String alias : PROVINCE_ALIASES.keySet()) {
			if (alias.length() >= 3 && q.contains(alias))
				return true;
		}
		for (String province : VALID_PROVINCES) {
			if (province != null && !province.isBlank()
					&& q.contains(province.toLowerCase()))
				return true;
		}
		return false;
	}

        public AiFilterMetaResponseDTO getAiFilterMeta() {
                List<String> provinces = warehouseRepository.findDistinctProvinces();

                List<AiFilterMetaResponseDTO.CertInfo> certs = certificationTypeRepository.findAll().stream()
                                .filter(c -> c.getLabel() != null)
                                .collect(java.util.stream.Collectors.toMap(
                                        c -> c.getLabel().toLowerCase(),
                                        c -> c,
                                        (existing, replacement) -> existing
                                ))
                                .values().stream()
                                .map(c -> new AiFilterMetaResponseDTO.CertInfo(c.getId().toString(), c.getLabel(), c.getDescription()))
                                .toList();

                List<Object[]> tempResult = warehouseSectionRepository.findTemperatureRange();
                List<Object[]> capResult = warehouseSectionRepository.findCapacityRange();
                List<Object[]> priceResult = priceTierRepository.findActivePriceRange();
                List<String> warehouseSectionLabels = warehouseSectionRepository.findDistinctLabels();
                List<String> priceTierLabels = priceTierRepository.findDistinctLabels();

                return new AiFilterMetaResponseDTO(
                                provinces, certs,
                                extractRange(tempResult, 0, -30.0),
                                extractRange(tempResult, 1, 15.0),
                                extractRange(capResult, 1, 0.0),
                                extractRange(capResult, 0, 10000.0),
                                extractRange(priceResult, 1, 0.0),
                                extractRange(priceResult, 0, 1000000.0),
                                warehouseSectionLabels,
                                priceTierLabels);
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