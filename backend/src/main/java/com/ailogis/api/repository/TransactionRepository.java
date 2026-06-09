package com.ailogis.api.repository;

import com.ailogis.api.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerIdAndStatus(Long buyerId, String status);
}