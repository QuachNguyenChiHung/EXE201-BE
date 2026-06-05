package com.ailogis.api.dto;

public record CertDTO(
        String certID,
        String label,
        String labelDesc,
        String update,
        String pdfLink
) {}