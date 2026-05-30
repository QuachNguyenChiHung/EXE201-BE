package com.ailogis.api.repository;

import com.ailogis.api.entity.PriceTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {
}