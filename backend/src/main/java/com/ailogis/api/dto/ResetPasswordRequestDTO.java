package com.ailogis.api.dto;

public record ResetPasswordRequestDTO(String email, String otp, String newPassword) {}
