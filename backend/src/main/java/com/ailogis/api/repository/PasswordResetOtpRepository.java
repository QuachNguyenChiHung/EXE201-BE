package com.ailogis.api.repository;

import com.ailogis.api.entity.PasswordResetOtp;
import com.ailogis.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByUserAndConsumedFalseOrderByCreatedAtDesc(User user);
}
