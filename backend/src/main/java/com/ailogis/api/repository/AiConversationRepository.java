package com.ailogis.api.repository;

import com.ailogis.api.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    long countByUserId(Long userId);

    List<AiConversation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT SUM(a.totalInputTokens + a.totalOutputTokens) FROM AiConversation a WHERE a.user.id = :userId")
    Long sumTokenUsageByUserId(@Param("userId") Long userId);
}