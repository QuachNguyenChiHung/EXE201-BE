package com.ailogis.api.repository;

import com.ailogis.api.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Query("SELECT us FROM UserSession us WHERE us.user.id = :userId AND us.logoutAt IS NULL ORDER BY us.loginAt DESC LIMIT 1")
    Optional<UserSession> findLatestActiveSession(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT us.user.id) FROM UserSession us WHERE us.loginAt >= :dateTime")
    long countActiveUsersSince(@Param("dateTime") LocalDateTime dateTime);
}