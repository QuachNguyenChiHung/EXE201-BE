package com.ailogis.api.repository;

import com.ailogis.api.entity.WarehouseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseSectionRepository extends JpaRepository<WarehouseSection, Long> {

    @Query("SELECT MIN(s.tempMin), MAX(s.tempMax) FROM WarehouseSection s WHERE s.warehouse.status IN ('ACTIVE', 'RENTED')")
    List<Object[]> findTemperatureRange();

    @Query("SELECT MIN(s.availableCapacity), MAX(s.availableCapacity) FROM WarehouseSection s WHERE s.warehouse.status IN ('ACTIVE', 'RENTED')")
    List<Object[]> findCapacityRange();
}