package com.ailogis.api.repository;

import com.ailogis.api.entity.AiSubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSubscriptionTierRepository extends JpaRepository<AiSubscriptionTier, Long> {
}