package com.ailogis.api.service;

import com.ailogis.api.dto.ForgotPasswordResponseDTO;
import com.ailogis.api.dto.MessageResponseDTO;
import com.ailogis.api.dto.ResetPasswordRequestDTO;
import com.ailogis.api.dto.VerifyOtpRequestDTO;
import com.ailogis.api.entity.PasswordResetOtp;
import com.ailogis.api.entity.User;
import com.ailogis.api.enums.Role;
import com.ailogis.api.repository.PasswordResetOtpRepository;
import com.ailogis.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final JavaMailSender mailSender;

    @Value("${app.otp.expiration-minutes:10}")
    private int expirationMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${app.mail.from:no-reply@logicha.io.vn}")
    private String mailFrom;

    private static final String GENERIC_SUCCESS =
            "Nếu email tồn tại trong hệ thống, mã xác thực đã được gửi. Vui lòng kiểm tra hộp thư.";

    @Transactional
    public ForgotPasswordResponseDTO requestOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email không được để trống!");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        // Always return a generic success to avoid email enumeration.
        if (userOpt.isEmpty()) {
            log.info("Forgot password requested for non-existent email: {}", normalizedEmail);
            return new ForgotPasswordResponseDTO(GENERIC_SUCCESS, resendCooldownSeconds);
        }

        User user = userOpt.get();

        // Employees are not allowed to reset via OTP — they must use the profile
        // self-service flow. Reject explicitly so the FE can show a clear notice
        // instead of letting them advance to an OTP step that would fail.
        if (user.getRole() == Role.EMPLOYEE) {
            log.info("Forgot password (OTP) rejected for EMPLOYEE account: {}", normalizedEmail);
            throw new RuntimeException(
                    "Tài khoản nhân viên không thể đặt lại mật khẩu qua OTP. "
                            + "Vui lòng liên hệ quản trị viên hoặc dùng chức năng đổi mật khẩu trong trang cá nhân.");
        }

        LocalDateTime now = LocalDateTime.now();

        // Enforce resend cooldown based on the most recent OTP issued for this user.
        Optional<PasswordResetOtp> existingOpt =
                otpRepository.findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user);
        if (existingOpt.isPresent()) {
            PasswordResetOtp existing = existingOpt.get();
            long secondsSinceLastSend = Duration.between(existing.getLastSentAt(), now).getSeconds();
            if (secondsSinceLastSend < resendCooldownSeconds) {
                int remaining = (int) (resendCooldownSeconds - secondsSinceLastSend);
                throw new RuntimeException(
                        "Vui lòng chờ " + remaining + " giây trước khi yêu cầu mã mới.");
            }
            // Invalidate the previous OTP so only one is active at a time.
            existing.setConsumed(true);
            otpRepository.save(existing);
        }

        String code = generateNumericOtp(otpLength);
        String hash = sha256Hex(code);

        PasswordResetOtp otp = PasswordResetOtp.builder()
                .user(user)
                .otpHash(hash)
                .expiresAt(now.plusMinutes(expirationMinutes))
                .consumed(false)
                .attempts(0)
                .lastSentAt(now)
                .createdAt(now)
                .build();
        otpRepository.save(otp);

        sendOtpEmail(user, code);

        return new ForgotPasswordResponseDTO(GENERIC_SUCCESS, resendCooldownSeconds);
    }

    @Transactional
    public MessageResponseDTO verifyOtp(VerifyOtpRequestDTO dto) {
        if (dto == null || dto.email() == null || dto.email().isBlank()) {
            throw new RuntimeException("Email không được để trống!");
        }
        if (dto.otp() == null || dto.otp().isBlank()) {
            throw new RuntimeException("Mã xác thực không được để trống!");
        }

        String normalizedEmail = dto.email().trim().toLowerCase();
        String otpInput = dto.otp().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Mã xác thực không hợp lệ hoặc đã hết hạn!"));

        if (user.getRole() == Role.EMPLOYEE) {
            throw new RuntimeException("Tài khoản nhân viên không thể đặt lại mật khẩu qua OTP!");
        }

        PasswordResetOtp otp = otpRepository
                .findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("Mã xác thực không hợp lệ hoặc đã hết hạn!"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Mark as consumed so the user must request a fresh OTP.
            otp.setConsumed(true);
            otpRepository.save(otp);
            throw new RuntimeException("Mã xác thực đã hết hạn, vui lòng yêu cầu mã mới!");
        }

        if (otp.getAttempts() >= maxAttempts) {
            otp.setConsumed(true);
            otpRepository.save(otp);
            throw new RuntimeException("Bạn đã nhập sai quá nhiều lần, vui lòng yêu cầu mã mới!");
        }

        String inputHash = sha256Hex(otpInput);
        if (!inputHash.equals(otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            int remaining = maxAttempts - otp.getAttempts();
            throw new RuntimeException("Mã xác thực không đúng. Bạn còn " + remaining + " lần thử.");
        }

        // SUCCESS: do NOT mark consumed. The OTP must still be valid for the final /reset-password call.
        return new MessageResponseDTO("Mã xác thực hợp lệ.");
    }

    @Transactional
    public MessageResponseDTO resetPassword(ResetPasswordRequestDTO dto) {
        if (dto == null || dto.newPassword() == null || dto.newPassword().length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự!");
        }

        // Re-run the same OTP checks (no consume on success path of verifyOtp).
        verifyOtp(new VerifyOtpRequestDTO(dto.email(), dto.otp()));

        User user = userRepository.findByEmail(dto.email().trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("Mã xác thực không hợp lệ hoặc đã hết hạn!"));

        PasswordResetOtp otp = otpRepository
                .findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("Mã xác thực không hợp lệ hoặc đã hết hạn!"));

        // Now consume the OTP and update the password.
        otp.setConsumed(true);
        otpRepository.save(otp);

        user.setPassword(dto.newPassword());
        userRepository.save(user);

        return new MessageResponseDTO("Đặt lại mật khẩu thành công! Bạn có thể đăng nhập với mật khẩu mới.");
    }

    private void sendOtpEmail(User user, String code) {
        // Honor optional configuration. If MAIL_USERNAME is empty, log the OTP instead
        // of attempting to send — useful for local dev without a real SMTP account.
        if (mailSender == null) {
            log.warn("JavaMailSender not configured. OTP for {} is {}", user.getEmail(), code);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(user.getEmail());
            message.setSubject("Logicha - Mã xác thực đặt lại mật khẩu");
            message.setText(
                    "Xin chào " + (user.getFullName() != null ? user.getFullName() : user.getEmail()) + ",\n\n"
                            + "Mã xác thực để đặt lại mật khẩu Logicha của bạn là: " + code + "\n\n"
                            + "Mã có hiệu lực trong " + expirationMinutes + " phút. "
                            + "Vui lòng không chia sẻ mã này với bất kỳ ai.\n\n"
                            + "Trân trọng,\nĐội ngũ Logicha");
            mailSender.send(message);
        } catch (Exception ex) {
            // Do not leak the OTP, but log enough to debug in dev.
            log.error("Failed to send OTP email to {}. SMTP may be unconfigured.", user.getEmail(), ex);
            log.warn("DEV OTP for {}: {}", user.getEmail(), code);
        }
    }

    private String generateNumericOtp(int length) {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(rng.nextInt(10));
        }
        return sb.toString();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi hệ thống khi mã hóa mã xác thực!", ex);
        }
    }
}
