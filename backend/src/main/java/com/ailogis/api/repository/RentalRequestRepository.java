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
    List<RentalRequest> findByRenterId(Long renterId);
    List<RentalRequest> findByWarehouseOwnerId(Long ownerId);
    long countByStatus(RequestStatus status);

    @Query("SELECT COUNT(r) FROM RentalRequest r WHERE r.warehouse.owner.id = :ownerId AND r.status = 'PENDING' AND r.updatedAt >= :dateLimit")
    long countPendingRequestsByOwner(@Param("ownerId") Long ownerId, @Param("dateLimit") LocalDate dateLimit);
}