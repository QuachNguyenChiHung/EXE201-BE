package com.ailogis.api.repository;

import com.ailogis.api.entity.RentalRequest;
import com.ailogis.api.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
@Repository
public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {
    @Query("SELECT r FROM RentalRequest r WHERE r.renter.id = :renterId AND (:status IS NULL OR r.status = :status)")
    List<RentalRequest> findByRenterIdWithFilter(@Param("renterId") Long renterId, @Param("status") RequestStatus status);

    @Query("SELECT r FROM RentalRequest r WHERE r.warehouse.owner.id = :ownerId AND (:status IS NULL OR r.status = :status)")
    List<RentalRequest> findByWarehouseOwnerIdWithFilter(@Param("ownerId") Long ownerId, @Param("status") RequestStatus status);

    long countByStatus(RequestStatus status);

    @Query("SELECT COUNT(r) FROM RentalRequest r WHERE r.warehouse.owner.id = :ownerId AND r.status = 'PENDING' AND r.updatedAt >= :dateLimit")
    long countPendingRequestsByOwner(@Param("ownerId") Long ownerId, @Param("dateLimit") LocalDate dateLimit);

    long countByWarehouseIdAndStatus(Long warehouseId, RequestStatus status);

    List<RentalRequest> findByWarehouseIdAndStatus(Long warehouseId, RequestStatus status);
}