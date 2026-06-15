package com.ailogis.api.repository;

import com.ailogis.api.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerIdAndStatus(Long buyerId, String status);

    @Query("SELECT SUM(t.sponsor.pricingPerMonth) FROM Transaction t WHERE t.buyer.id = :buyerId AND t.status = 'COMPLETED' AND t.invoiceDate >= :startDate AND t.invoiceDate <= :endDate")
    Double sumSponsorBillingByDateRange(@Param("buyerId") Long buyerId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    List<Transaction> findByStatus(String status);
}