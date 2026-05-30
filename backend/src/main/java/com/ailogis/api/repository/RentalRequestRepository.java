package com.ailogis.api.repository;

import com.ailogis.api.entity.RentalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {
    List<RentalRequest> findByRenterId(Long renterId);
    List<RentalRequest> findByWarehouseOwnerId(Long ownerId);
}