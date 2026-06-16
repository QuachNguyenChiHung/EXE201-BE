package com.ailogis.api.repository;

import com.ailogis.api.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findByUserId(Long userId);

    Optional<Bookmark> findByUserIdAndWarehouseId(Long userId, Long warehouseId);
}