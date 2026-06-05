package com.ailogis.api.repository;

import com.ailogis.api.entity.Contract;
import com.ailogis.api.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    long countByStatus(ContractStatus status);
}