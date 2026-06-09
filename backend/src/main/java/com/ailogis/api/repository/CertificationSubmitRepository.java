package com.ailogis.api.repository;

import com.ailogis.api.entity.CertificationSubmit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertificationSubmitRepository extends JpaRepository<CertificationSubmit, Long> {
    boolean existsByTypeId(Long typeId);
}