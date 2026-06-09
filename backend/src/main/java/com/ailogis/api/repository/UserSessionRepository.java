package com.ailogis.api.repository;

import com.ailogis.api.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Query("SELECT us FROM UserSession us WHERE us.user.id = :userId AND us.logoutAt IS NULL ORDER BY us.loginAt DESC LIMIT 1")
    Optional<UserSession> findLatestActiveSession(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT us.user.id) FROM UserSession us WHERE us.loginAt >= :dateTime")
    long countActiveUsersSince(@Param("dateTime") LocalDateTime dateTime);

    // Lấy thống kê đa luồng (Role + Time)
    @Query("SELECT CAST(us.loginDate AS string), u.role, COUNT(DISTINCT u.id) " +
            "FROM UserSession us JOIN us.user u " +
            "WHERE us.loginDate BETWEEN :startDate AND :endDate " +
            "GROUP BY us.loginDate, u.role ORDER BY us.loginDate")
    List<Object[]> countActiveUsersByDateAndRole(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Lấy theo giờ
    @Query(value = "SELECT EXTRACT(HOUR FROM s.loginat) as hr, u.role, COUNT(DISTINCT s.id_user) " +
            "FROM sessions s JOIN users u ON s.id_user = u.id " +
            "WHERE s.logindate = :date " +
            "GROUP BY hr, u.role ORDER BY hr", nativeQuery = true)
    List<Object[]> countActiveUsersByHourAndRoleNative(@Param("date") java.time.LocalDate date);
}