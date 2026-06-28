package com.ailogis.api.repository;

import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.WarehouseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    // Tìm danh sách kho đã được duyệt (dành cho Renter)
    Page<Warehouse> findByStatus(WarehouseStatus status, Pageable pageable);
    List<Warehouse> findByStatus(com.ailogis.api.enums.WarehouseStatus status);

    // Tìm kho theo chủ sở hữu (dành cho Owner)
    Page<Warehouse> findByOwnerId(Long ownerId, Pageable pageable);
    List<Warehouse> findByOwnerId(Long ownerId);

    long countByStatus(WarehouseStatus status);

    long countBySponsorTypeId(Long sponsorTypeId);

    // API Popular: Ưu tiên Sponsor ->  Sponsor Tier (Vàng -> Bạc) -> Số lượng Request (30 ngày) -> Rating cao
    @Query("SELECT w FROM Warehouse w LEFT JOIN w.sponsorType st WHERE w.status = 'ACTIVE' " +
            "ORDER BY w.isSponsor DESC, " +
            "st.priorityLevel ASC, " +
            "(SELECT COUNT(req) FROM RentalRequest req WHERE req.warehouse = w AND req.submitAt >= :thirtyDaysAgo) DESC, " +
            "(SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) DESC")
    Page<Warehouse> findPopularWarehouses(@Param("thirtyDaysAgo") LocalDate thirtyDaysAgo, Pageable pageable);

    // API Search
    // keyword must NEVER be null — caller passes "" if absent to avoid
    // Postgres inferring `bytea` for the LIKE-bound parameter.
    // hasProvinces/hasCerts flags replace IS EMPTY (not supported on params in Hibernate 7).
    @Query("SELECT w FROM Warehouse w " +
            "LEFT JOIN w.sponsorType st " +
            "WHERE w.status IN ('ACTIVE', 'RENTED') " +
            "AND (:keyword = '' OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "    OR LOWER(w.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "    OR LOWER(w.locationAddressText) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "    OR LOWER(w.locationCommune) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:hasProvinces = false OR w.locationProvince IN :provinces) " +
            "AND (:minArea IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity >= :minArea)) " +
            "AND (:maxArea IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity <= :maxArea)) " +
            "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value >= :minPrice)) " +
            "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value <= :maxPrice)) " +
            "AND (:minRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) >= :minRating) " +
            "AND (:maxRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) <= :maxRating) " +
            "AND (:hasCerts = false OR EXISTS (SELECT 1 FROM CertificationSubmit cs WHERE cs.warehouse = w AND cs.type.id IN :certTypeIds AND cs.status = 'VERIFIED')) " +
            "ORDER BY w.isSponsor DESC, st.priorityLevel ASC, w.id DESC")
    Page<Warehouse> searchWarehouses(
            @Param("keyword") String keyword,
            @Param("hasProvinces") boolean hasProvinces,
            @Param("provinces") List<String> provinces,
            @Param("minArea") Double minArea, @Param("maxArea") Double maxArea,
            @Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating,
            @Param("hasCerts") boolean hasCerts,
            @Param("certTypeIds") List<Long> certTypeIds, Pageable pageable);

    // Lấy danh sách các tỉnh/thành phố KHÔNG TRÙNG LẶP từ các kho bãi đang hoạt động
    @Query("SELECT DISTINCT w.locationProvince FROM Warehouse w WHERE w.locationProvince IS NOT NULL AND w.status IN ('ACTIVE', 'RENTED')")
    List<String> findDistinctProvinces();

    // Lấy danh sách kho của Owner có hỗ trợ lọc Status và Phân trang
    @Query("SELECT w FROM Warehouse w WHERE w.owner.id = :ownerId AND (:status IS NULL OR w.status = :status) ORDER BY w.id DESC")
    Page<Warehouse> findByOwnerIdWithFilterPaged(@Param("ownerId") Long ownerId, @Param("status") WarehouseStatus status, Pageable pageable);

    // AI Search: supports temp range, capacity range, and dynamic sort
    // Note: keyword/province must NEVER be null (caller must pass "" if absent).
    // Passing null causes Postgres to infer `bytea` for the LIKE-bound parameter → "function lower(bytea) does not exist".
    @Query(value = "SELECT w FROM Warehouse w " +
            "LEFT JOIN w.sponsorType st " +
            "WHERE w.status IN ('ACTIVE', 'RENTED') " +
            "AND (LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:hasProvinces = false OR w.locationProvince IN :provinces) " +
            "AND (:minTemp IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.tempMin <= :minTemp)) " +
            "AND (:maxTemp IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.tempMax >= :maxTemp)) " +
            "AND (:minAvailableCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity >= :minAvailableCap)) " +
            "AND (:maxAvailableCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity <= :maxAvailableCap)) " +
            "AND (:minTotalCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.totalCapacity >= :minTotalCap)) " +
            "AND (:maxTotalCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.totalCapacity <= :maxTotalCap)) " +
            "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value >= :minPrice)) " +
            "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value <= :maxPrice)) " +
            "AND (:minRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) >= :minRating) " +
            "AND (:maxRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) <= :maxRating) " +
            "ORDER BY " +
            "  w.isSponsor DESC, st.priorityLevel ASC, " +
            "  CASE WHEN :sortType = 'price' THEN (SELECT COALESCE(MIN(pt.value), 999999999) FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w) END ASC, " +
            "  CASE WHEN :sortType = 'rating' THEN (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) END DESC, " +
            "  w.id DESC",
            countQuery = "SELECT COUNT(w) FROM Warehouse w WHERE w.status IN ('ACTIVE', 'RENTED') " +
                    "AND (LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:hasProvinces = false OR w.locationProvince IN :provinces) " +
                    "AND (:minTemp IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.tempMin <= :minTemp)) " +
                    "AND (:maxTemp IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.tempMax >= :maxTemp)) " +
                    "AND (:minAvailableCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity >= :minAvailableCap)) " +
                    "AND (:maxAvailableCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.availableCapacity <= :maxAvailableCap)) " +
                    "AND (:minTotalCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.totalCapacity >= :minTotalCap)) " +
                    "AND (:maxTotalCap IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec WHERE sec.warehouse = w AND sec.totalCapacity <= :maxTotalCap)) " +
                    "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value >= :minPrice)) " +
                    "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM WarehouseSection sec JOIN sec.priceTiers pt WHERE sec.warehouse = w AND pt.value <= :maxPrice)) " +
                    "AND (:minRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) >= :minRating) " +
                    "AND (:maxRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) <= :maxRating)")
    Page<Warehouse> searchWarehousesByCriteria(
            @Param("keyword") String keyword,
            @Param("hasProvinces") boolean hasProvinces,
            @Param("provinces") List<String> provinces,
            @Param("minTemp") Double minTemp,
            @Param("maxTemp") Double maxTemp,
            @Param("minAvailableCap") Double minAvailableCap,
            @Param("maxAvailableCap") Double maxAvailableCap,
            @Param("minTotalCap") Double minTotalCap,
            @Param("maxTotalCap") Double maxTotalCap,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("minRating") Double minRating,
            @Param("maxRating") Double maxRating,
            @Param("sortType") String sortType,
            Pageable pageable);
}