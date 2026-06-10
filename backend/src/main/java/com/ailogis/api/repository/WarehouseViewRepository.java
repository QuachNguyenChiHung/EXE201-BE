package com.ailogis.api.repository;

import com.ailogis.api.entity.WarehouseView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseViewRepository extends JpaRepository<WarehouseView, Long> {

    @Query("SELECT CAST(wv.viewDate AS string), COUNT(wv) FROM WarehouseView wv WHERE wv.warehouse.id = :warehouseId GROUP BY wv.viewDate")
    List<Object[]> countViewsByDateForWarehouse(@Param("warehouseId") Long warehouseId);

    // Nhóm lượt view theo ngày của 1 kho cụ thể
    @Query("SELECT CAST(wv.viewDate AS string), COUNT(wv) " +
            "FROM WarehouseView wv " +
            "WHERE wv.warehouse.id = :warehouseId AND wv.viewDate >= :startDate " +
            "GROUP BY wv.viewDate ORDER BY wv.viewDate")
    List<Object[]> countViewsByDateForWarehouse(@Param("warehouseId") Long warehouseId, @Param("startDate") java.time.LocalDate startDate);
}