package com.ailogis.api.repository;

import com.ailogis.api.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    long countByUserId(Long userId);

    List<AiConversation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT SUM(a.totalInputTokens + a.totalOutputTokens) FROM AiConversation a WHERE a.user.id = :userId")
    Long sumTokenUsageByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(a.totalInputTokens) FROM AiConversation a WHERE a.user.id = :userId AND a.createdAt >= :start AND a.createdAt < :end")
    Long sumInputTokensByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT SUM(a.totalOutputTokens) FROM AiConversation a WHERE a.user.id = :userId AND a.createdAt >= :start AND a.createdAt < :end")
    Long sumOutputTokensByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}