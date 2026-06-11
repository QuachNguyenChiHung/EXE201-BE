package com.ailogis.api.repository;

import com.ailogis.api.entity.SponsorTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SponsorTierRepository extends JpaRepository<SponsorTier, Long> {
}