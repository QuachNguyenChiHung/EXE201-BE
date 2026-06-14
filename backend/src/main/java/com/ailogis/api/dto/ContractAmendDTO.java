package com.ailogis.api.dto;

import java.time.LocalDate;

public record ContractAmendDTO(
        LocalDate startAt,
        LocalDate endAt,
        String paymentTerm,
        String penaltyClause,
        String specialTerm
) {}