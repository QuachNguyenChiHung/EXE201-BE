package com.ailogis.api.repository;

import com.ailogis.api.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByWarehouseId(Long warehouseId);

    // Tìm review cũ của user đối với 1 kho bãi
    Optional<Review> findByUserIdAndWarehouseId(Long userId, Long warehouseId);
}