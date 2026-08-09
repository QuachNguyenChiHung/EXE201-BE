package com.ailogis.api.exception;

import com.ailogis.api.dto.ErrorResponseDTO;
import com.ailogis.api.security.LockUntilContext;
import com.ailogis.api.security.RemainingAttemptsContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Sai email hoặc mật khẩu. Trả về HTTP 401 để client phân biệt được với
     * các lỗi nghiệp vụ khác (400). Response body có thể kèm
     * {@code remainingAttempts} và {@code lockUntil} được publish từ
     * {@code LoginAttemptService.onFailure} qua thread-local.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Đăng nhập thất bại (sai email hoặc mật khẩu)");
        try {
            Integer remaining = RemainingAttemptsContext.get();
            LocalDateTime lockUntil = LockUntilContext.get();
            ErrorResponseDTO body = ErrorResponseDTO.of(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Bad Credentials",
                    "Email hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại.",
                    remaining, lockUntil);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        } finally {
            RemainingAttemptsContext.clear();
            LockUntilContext.clear();
        }
    }

    /**
     * Tài khoản đang bị khóa tạm do nhập sai mật khẩu quá nhiều lần.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponseDTO> handleLocked(LockedException ex) {
        log.warn("Tài khoản đang bị khóa tạm: {}", ex.getMessage());
        try {
            Integer remaining = RemainingAttemptsContext.get();
            LocalDateTime lockUntil = LockUntilContext.get();
            if (remaining == null) {
                remaining = 0;
            }
            ErrorResponseDTO body = ErrorResponseDTO.of(
                    HttpStatus.LOCKED.value(),
                    "Account Locked",
                    "Bạn đã nhập sai mật khẩu quá nhiều lần. "
                            + "Tài khoản đã bị khóa tạm thời 5 phút để bảo vệ an toàn. "
                            + "Vui lòng thử lại sau.",
                    remaining, lockUntil);
            return ResponseEntity.status(HttpStatus.LOCKED).body(body);
        } finally {
            RemainingAttemptsContext.clear();
            LockUntilContext.clear();
        }
    }

    /**
     * Tài khoản bị vô hiệu hóa (status = INACTIVE).
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponseDTO> handleDisabled(DisabledException ex) {
        log.warn("Tài khoản bị vô hiệu hóa: {}", ex.getMessage());
        try {
            ErrorResponseDTO error = ErrorResponseDTO.of(
                    HttpStatus.FORBIDDEN.value(),
                    "Forbidden",
                    "Tài khoản đã bị vô hiệu hóa, vui lòng liên hệ quản trị viên.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        } finally {
            RemainingAttemptsContext.clear();
            LockUntilContext.clear();
        }
    }

    /**
     * Bắt tất cả các lỗi nghiệp vụ chủ động ném ra bằng RuntimeException
     * Trả về HTTP Status: 400 Bad Request
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRuntimeException(RuntimeException ex) {
        log.warn("Nghiệp vụ bị chặn: {}", ex.getMessage());

        ErrorResponseDTO error = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Bắt các lỗi hệ thống không lường trước được (NullPointer, IndexOutOfBounds...)
     * Trả về HTTP Status: 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneralException(Exception ex) {
        log.error("Lỗi hệ thống nghiêm trọng: ", ex);

        ErrorResponseDTO error = ErrorResponseDTO.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Đã có lỗi hệ thống xảy ra, vui lòng liên hệ admin!");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Bắt lỗi Validation (Khi @Valid ở Controller phát hiện dữ liệu gửi lên sai quy tắc)
     * Trả về HTTP Status: 400 Bad Request kèm thông báo lỗi cụ thể
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        log.warn("Dữ liệu không hợp lệ: {}", errorMessage);

        ErrorResponseDTO error = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
