package com.ailogis.api.controller;

import com.ailogis.api.service.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
}