package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "sponsor_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer priorityLevel;
    private Double pricingPerMonth;
    private Double yearPackSale;
    private String label;
    private LocalDate updatedAt;

    @Builder.Default
    private Boolean isActive = true;
}