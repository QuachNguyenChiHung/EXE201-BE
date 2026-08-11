package com.ailogis.api.controller;

import com.ailogis.api.dto.TransactionResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${frontend.payment-success-url}")
    private String successUrl;

    @Value("${frontend.payment-fail-url}")
    private String failUrl;

    // Người dùng được PayOS redirect về sau khi thanh toán (hoặc hủy) trên trang checkout
    @GetMapping("/payos-return")
    public void payosReturn(@RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String cancel,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {
        log.info("PayOS return: orderCode={}, code={}, cancel={}, status={}", orderCode, code, cancel, status);

        boolean isSuccess = false;
        if (orderCode != null) {
            try {
                long parsedOrderCode = Long.parseLong(orderCode);
                if ("true".equalsIgnoreCase(cancel)) {
                    // PayOS đã báo người dùng chủ động hủy - hủy luôn thay vì đi hỏi lại trạng thái.
                    paymentService.cancelPayOSTransaction(parsedOrderCode);
                } else {
                    isSuccess = paymentService.verifyAndApplyByOrderCode(parsedOrderCode);
                }
            } catch (NumberFormatException e) {
                log.warn("orderCode không hợp lệ từ PayOS return-url: {}", orderCode);
            }
        }

        if (isSuccess) {
            response.sendRedirect(successUrl);
        } else {
            response.sendRedirect(failUrl);
        }
    }

    // Webhook server-to-server từ PayOS - nguồn xác thực chính cho kết quả thanh toán
    @PostMapping("/payos-webhook")
    public ResponseEntity<Void> payosWebhook(@RequestBody Map<String, Object> body) {
        boolean valid = paymentService.handlePayOSWebhook(body);
        if (valid) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    // Đăng ký URL webhook với PayOS - thao tác 1 lần, thực hiện thủ công, KHÔNG gọi tự động lúc khởi động app
    // Quyền truy cập (EMPLOYEE) được chặn theo path tại SecurityConfig, không dùng @PreAuthorize (method security chưa được bật trong dự án này)
    @PostMapping("/register-webhook")
    public ResponseEntity<String> registerWebhook() {
        return ResponseEntity.ok(paymentService.registerPayOSWebhook());
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionResponseDTO>> getMyTransactionHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUser().getId();
        List<TransactionResponseDTO> transactions = paymentService.getTransactionHistory(userId);

        return ResponseEntity.ok(transactions);
    }
}
