package com.ailogis.api.repository;

import com.ailogis.api.entity.Warehouse;
import com.ailogis.api.enums.WarehouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    // Tìm danh sách kho đã được duyệt (dành cho Renter)
    List<Warehouse> findByStatus(WarehouseStatus status);

    // Tìm kho theo chủ sở hữu (dành cho Owner)
    List<Warehouse> findByOwnerId(Long ownerId);

    long countByStatus(WarehouseStatus status);

    long countBySponsorTypeId(Long sponsorTypeId);
}