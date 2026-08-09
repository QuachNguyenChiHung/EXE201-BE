package com.ailogis.api.dto;

public record ForgotPasswordResponseDTO(String message, Integer cooldownSeconds) {}
