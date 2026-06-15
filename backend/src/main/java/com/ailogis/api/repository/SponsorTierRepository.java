package com.ailogis.api.repository;

import com.ailogis.api.entity.SponsorTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SponsorTierRepository extends JpaRepository<SponsorTier, Long> {
    @Query("SELECT DISTINCT s FROM SponsorTier s LEFT JOIN Warehouse w ON w.sponsorType.id = s.id AND w.owner.id = :ownerId AND w.isSponsor = true " +
            "WHERE s.isActive = true OR w.id IS NOT NULL")
    List<SponsorTier> findActiveAndPurchasedByOwner(@Param("ownerId") Long ownerId);
}