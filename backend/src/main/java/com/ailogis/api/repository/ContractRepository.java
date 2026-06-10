package com.ailogis.api.repository;

import com.ailogis.api.entity.Contract;
import com.ailogis.api.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    long countByStatus(ContractStatus status);
    // Tìm các hợp đồng mà User này tham gia với tư cách là Owner HOẶC Renter
    @Query("SELECT c FROM Contract c WHERE c.owner.id = :userId OR c.renter.id = :userId")
    List<Contract> findByOwnerIdOrRenterId(@Param("userId") Long userId);
}