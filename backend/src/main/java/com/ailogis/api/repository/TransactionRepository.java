package com.ailogis.api.repository;

import com.ailogis.api.entity.Transaction;
import com.ailogis.api.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerIdAndStatus(Long buyerId, String status);

    @Query("SELECT SUM(t.sponsor.pricingPerMonth) FROM Transaction t WHERE t.buyer.id = :buyerId AND t.status = 'COMPLETED' AND t.invoiceDate >= :startDate AND t.invoiceDate <= :endDate")
    Double sumSponsorBillingByDateRange(@Param("buyerId") Long buyerId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.buyer.id = :buyerId AND t.status = 'COMPLETED'")
    Double sumTotalBillingByBuyerId(@Param("buyerId") Long buyerId);

    List<Transaction> findByStatus(String status);

    Transaction findByRentalRequestIdAndStatus(Long rentalRequestId, String status);

    List<Transaction> findByBuyerIdOrderByIdDesc(Long buyerId);

    // ==== Employee Transaction Analytics ====

    @Query(value = "SELECT t FROM Transaction t JOIN FETCH t.buyer b " +
            "WHERE (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:buyerRole IS NULL OR b.role = :buyerRole) " +
            "AND (CAST(:startDate AS timestamp) IS NULL OR t.createdAt >= :startDate) " +
            "AND (CAST(:endDate AS timestamp) IS NULL OR t.createdAt < :endDate)",
            countQuery = "SELECT COUNT(t) FROM Transaction t JOIN t.buyer b " +
                    "WHERE (:type IS NULL OR t.type = :type) " +
                    "AND (:status IS NULL OR t.status = :status) " +
                    "AND (:buyerRole IS NULL OR b.role = :buyerRole) " +
                    "AND (CAST(:startDate AS timestamp) IS NULL OR t.createdAt >= :startDate) " +
                    "AND (CAST(:endDate AS timestamp) IS NULL OR t.createdAt < :endDate)")
    Page<Transaction> searchAllTransactions(@Param("type") String type, @Param("status") String status,
            @Param("buyerRole") Role buyerRole, @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, Pageable pageable);

    @Query("SELECT t.type, SUM(t.amount), COUNT(t) FROM Transaction t WHERE t.amount IS NOT NULL GROUP BY t.type")
    List<Object[]> sumAndCountByType();

    @Query("SELECT t.buyer.role, SUM(t.amount) FROM Transaction t WHERE t.amount IS NOT NULL GROUP BY t.buyer.role")
    List<Object[]> sumAmountByBuyerRole();

    @Query("SELECT t FROM Transaction t JOIN FETCH t.buyer WHERE t.amount IS NOT NULL ORDER BY t.amount DESC")
    List<Transaction> findTopByAmountDesc(Pageable pageable);

    @Query(value = "SELECT DATE_TRUNC(:granularity, t.createdat) AS bucket, u.role AS buyer_role, SUM(t.amount) AS total " +
            "FROM transactions t JOIN users u ON u.id = t.id_buyer " +
            "WHERE t.createdat >= :startDateTime AND t.createdat < :endDateTime AND t.amount IS NOT NULL " +
            "GROUP BY bucket, u.role ORDER BY bucket", nativeQuery = true)
    List<Object[]> sumRevenueByGranularityAndRole(@Param("granularity") String granularity,
            @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);
}