package com.ailogis.api.dto;

public record RegisterRequestDTO(
                String email,
                String password,
                String fullName,
                String phone,
                String role, // "RENTER" hoặc "OWNER"
                String companyName,
                String companyTaxCode) {
}