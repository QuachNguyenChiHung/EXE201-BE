package com.ailogis.api.security;

import com.ailogis.api.entity.User;
import com.ailogis.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Theo dõi số lần đăng nhập thất bại liên tiếp của mỗi tài khoản.
 * <p>
 * Sau {@code app.login.max-failed-attempts} lần sai mật khẩu, tài khoản sẽ bị
 * khóa tạm trong {@code app.login.lock-duration-minutes} phút — trong thời gian
 * đó người dùng không thể đăng nhập. Khi đăng nhập thành công, bộ đếm sẽ được
 * reset về 0.
 * <p>
 * Reset semantics — mỗi lần đăng nhập thất bại, bộ đếm sẽ được đánh giá lại:
 * <ul>
 *   <li>Nếu tài khoản đang trong thời gian khóa tạm mà khóa đã hết hạn, reset
 *       bộ đếm về 0 và KHÔNG tính lần thử hiện tại — đây là "reset trigger"
 *       để mở khóa trên UI. Người dùng có đủ 5 lần thử mới.</li>
 *   <li>Nếu lần thử hiện tại cách lần sai trước hơn
 *       {@code app.login.lock-duration-minutes} phút và bộ đếm chưa đạt ngưỡng
 *       khóa, reset bộ đếm về 0 và KHÔNG tính lần thử hiện tại — idle reset.
 *       Người dùng đã rời đi và quay lại; chu kỳ mới bắt đầu từ đây.</li>
 * </ul>
 * Số lần thử còn lại được publish qua {@link RemainingAttemptsContext} để
 * response body có thể trả về giá trị chính xác cho FE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final UserRepository userRepository;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lock-duration-minutes:5}")
    private int lockDurationMinutes;

    /**
     * Bắt event đăng nhập thất bại (sai mật khẩu, sai email…). Lưu ý:
     * event này chỉ được bắn ra khi người dùng tồn tại — vì vậy ta cố gắng tải
     * user theo email (principal) để tăng bộ đếm. Nếu email không tồn tại thì
     * bỏ qua (chống email enumeration).
     */
    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        String email = principal == null ? null : principal.toString();
        if (email == null || email.isBlank()) {
            return;
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            // Không tiết lộ email nào tồn tại — bỏ qua.
            log.info("Failed login attempt for unknown email: {}", email);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int previousAttempts = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
        LocalDateTime lockUntil = user.getLockUntil();
        LocalDateTime lastFailedAt = user.getLastFailedAt();

        try {
            // RESET PATH 1 — Khóa tạm đã hết hạn. Reset bộ đếm về 0 và KHÔNG
            // tính lần thử hiện tại vào chu kỳ mới — đây là reset trigger.
            // Người dùng có đủ 5 lần thử mới từ lần nhập tiếp theo.
            if (lockUntil != null && now.isAfter(lockUntil)) {
                user.setFailedAttempts(0);
                user.setLockUntil(null);
                user.setLastFailedAt(now);
                userRepository.save(user);
                RemainingAttemptsContext.set(maxFailedAttempts); // 5 — full cycle available
                log.info("Lock expired for {}, counter reset to 0 (no increment for this attempt)", email);
                return;
            }

            // RESET PATH 2 — Idle reset. Lần thử hiện tại cách lần sai trước
            // hơn lockDurationMinutes phút, không bị khóa, nhưng người dùng đã
            // rời đi và quay lại. Reset bộ đếm về 0 và KHÔNG tính lần thử hiện
            // tại — chu kỳ mới bắt đầu từ đây.
            if (lastFailedAt != null
                    && previousAttempts > 0
                    && previousAttempts < maxFailedAttempts
                    && now.isAfter(lastFailedAt.plusMinutes(lockDurationMinutes))) {
                log.info("Idle reset for {}: {} min since last failed attempt, resetting counter",
                        email, lockDurationMinutes);
                user.setFailedAttempts(0);
                user.setLastFailedAt(now);
                userRepository.save(user);
                RemainingAttemptsContext.set(maxFailedAttempts); // 5 — full cycle available
                return;
            }

            // NORMAL PATH — tính lần thử hiện tại vào bộ đếm.
            int attempts = previousAttempts + 1;
            user.setFailedAttempts(attempts);
            user.setLastFailedAt(now);

            if (attempts >= maxFailedAttempts) {
                LocalDateTime newLockUntil = now.plusMinutes(lockDurationMinutes);
                user.setLockUntil(newLockUntil);
                log.warn("Account locked for {} minutes due to {} failed login attempts: {}",
                        lockDurationMinutes, attempts, email);
                RemainingAttemptsContext.set(0); // 0 — đang bị khóa
                LockUntilContext.set(newLockUntil);
            } else {
                log.info("Failed login attempt {}/{} for {}", attempts, maxFailedAttempts, email);
                RemainingAttemptsContext.set(maxFailedAttempts - attempts);
            }

            userRepository.save(user);
        } catch (RuntimeException ex) {
            // Đảm bảo thread-local không leak nếu có lỗi xảy ra giữa chừng.
            RemainingAttemptsContext.clear();
            LockUntilContext.clear();
            throw ex;
        }
    }

    /**
     * Khi đăng nhập thành công, reset bộ đếm, xóa thời gian khóa và timestamp.
     */
    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal == null) {
            return;
        }
        String email = principal.toString();
        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return;
        }
        if ((user.getFailedAttempts() != null && user.getFailedAttempts() > 0)
                || user.getLockUntil() != null
                || user.getLastFailedAt() != null) {
            log.info("Resetting failed-attempts counter after successful login for {}", email);
        }
        user.setFailedAttempts(0);
        user.setLockUntil(null);
        user.setLastFailedAt(null);
        userRepository.save(user);
    }
}
