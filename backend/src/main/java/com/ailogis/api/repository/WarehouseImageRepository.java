package com.ailogis.api.repository;

import com.ailogis.api.entity.WarehouseImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseImageRepository extends JpaRepository<WarehouseImage, Long> {
}