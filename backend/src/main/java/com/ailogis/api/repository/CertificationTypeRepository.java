package com.ailogis.api.repository;

import com.ailogis.api.entity.CertificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertificationTypeRepository extends JpaRepository<CertificationType, Long> {
}