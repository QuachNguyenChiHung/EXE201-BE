package com.ailogis.api.repository;

import com.ailogis.api.entity.PriceTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    @Query("SELECT MIN(pt.value), MAX(pt.value) FROM PriceTier pt JOIN pt.section s WHERE s.warehouse.status IN ('ACTIVE', 'RENTED') AND pt.isActive = true")
    List<Object[]> findActivePriceRange();

    @Query("SELECT DISTINCT pt.label FROM PriceTier pt JOIN pt.section s WHERE s.warehouse.status IN ('ACTIVE', 'RENTED') AND pt.isActive = true AND pt.label IS NOT NULL AND pt.label <> ''")
    List<String> findDistinctLabels();
}