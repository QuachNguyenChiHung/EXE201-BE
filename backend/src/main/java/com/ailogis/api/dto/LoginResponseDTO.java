package com.ailogis.api.dto;
public record LoginResponseDTO(String token, String type, String email, String role) {}