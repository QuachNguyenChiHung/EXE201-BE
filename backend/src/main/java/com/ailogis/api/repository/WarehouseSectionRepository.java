package com.ailogis.api.repository;

import com.ailogis.api.entity.WarehouseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseSectionRepository extends JpaRepository<WarehouseSection, Long> {
}