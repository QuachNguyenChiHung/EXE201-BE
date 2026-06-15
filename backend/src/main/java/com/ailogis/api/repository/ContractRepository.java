package com.ailogis.api.repository;

import com.ailogis.api.entity.Contract;
import com.ailogis.api.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    long countByStatus(ContractStatus status);

    // Cho Employee
    @Query("SELECT c FROM Contract c WHERE (:status IS NULL OR c.status = :status)")
    List<Contract> findAllWithFilter(@Param("status") ContractStatus status);

    // Cho Owner/Renter
    @Query("SELECT c FROM Contract c WHERE (c.owner.id = :userId OR c.renter.id = :userId) AND (:status IS NULL OR c.status = :status)")
    List<Contract> findByOwnerIdOrRenterIdWithFilter(@Param("userId") Long userId, @Param("status") ContractStatus status);

    long countByOwnerIdAndStatus(Long ownerId, ContractStatus status);

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.owner.id = :ownerId AND c.status = 'ACTIVE' AND c.endAt <= :dateLimit")
    long countEndingContracts(@Param("ownerId") Long ownerId, @Param("dateLimit") LocalDate dateLimit);

    @Query("SELECT SUM(d.rentedArea) FROM Contract c JOIN c.request r JOIN r.details d WHERE d.section.id = :sectionId AND c.status = 'ACTIVE'")
    Double sumActiveRentedAreaBySection(@Param("sectionId") Long sectionId);

    @Query("SELECT c FROM Contract c WHERE c.request.warehouse.id = :warehouseId AND (:status IS NULL OR c.status = :status) ORDER BY c.id DESC")
    List<Contract> findByWarehouseIdWithFilter(@Param("warehouseId") Long warehouseId, @Param("status") ContractStatus status);
}