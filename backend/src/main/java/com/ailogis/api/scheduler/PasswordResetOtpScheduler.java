package com.ailogis.api.scheduler;

import com.ailogis.api.entity.PasswordResetOtp;
import com.ailogis.api.repository.PasswordResetOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetOtpScheduler {

    private final PasswordResetOtpRepository otpRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeExpiredOtps() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<PasswordResetOtp> stale = otpRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isBefore(cutoff))
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        otpRepository.deleteAll(stale);
        log.info("Đã dọn {} OTP đặt lại mật khẩu cũ.", stale.size());
    }
}
