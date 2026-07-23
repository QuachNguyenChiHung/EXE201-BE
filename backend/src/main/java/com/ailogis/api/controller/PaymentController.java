package com.ailogis.api.controller;

import com.ailogis.api.dto.TransactionResponseDTO;
import com.ailogis.api.entity.Transaction;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/vnpay-return")
    public void vnpayReturn(@RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
        boolean isSuccess = paymentService.processVNPayCallback(params);

        if (isSuccess) {
            response.sendRedirect(successUrl);
        } else {
            response.sendRedirect(failUrl);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionResponseDTO>> getMyTransactionHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUser().getId();
        List<TransactionResponseDTO> transactions = paymentService.getTransactionHistory(userId);

        return ResponseEntity.ok(transactions);
    }
}