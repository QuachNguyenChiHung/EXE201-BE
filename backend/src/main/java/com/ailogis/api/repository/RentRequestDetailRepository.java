package com.ailogis.api.repository;

import com.ailogis.api.entity.RentRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RentRequestDetailRepository extends JpaRepository<RentRequestDetail, Long> {
}