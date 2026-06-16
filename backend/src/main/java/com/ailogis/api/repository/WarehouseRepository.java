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
    @Query("SELECT DISTINCT w FROM Warehouse w " +
            "LEFT JOIN w.sections sec LEFT JOIN sec.priceTiers pt " +
            "WHERE w.status IN ('ACTIVE', 'RENTED') " +
            "AND (:province IS NULL OR w.locationProvince = :province) " +
            "AND (:isSponsor IS NULL OR w.isSponsor = :isSponsor) " +
            "AND (:minArea IS NULL OR sec.availableCapacity >= :minArea) " +
            "AND (:maxArea IS NULL OR sec.availableCapacity <= :maxArea) " +
            "AND (:minPrice IS NULL OR pt.value >= :minPrice) " +
            "AND (:maxPrice IS NULL OR pt.value <= :maxPrice) " +
            "AND (:minRating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.warehouse = w) >= :minRating)")
    Page<Warehouse> searchWarehouses(
            @Param("province") String province, @Param("isSponsor") Boolean isSponsor,
            @Param("minArea") Double minArea, @Param("maxArea") Double maxArea,
            @Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice,
            @Param("minRating") Double minRating, Pageable pageable);

    // Lấy danh sách các tỉnh/thành phố KHÔNG TRÙNG LẶP từ các kho bãi đang hoạt động
    @Query("SELECT DISTINCT w.locationProvince FROM Warehouse w WHERE w.locationProvince IS NOT NULL AND w.status IN ('ACTIVE', 'RENTED')")
    List<String> findDistinctProvinces();
}