package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "ai_subscription_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSubscriptionTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private String description;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Double price;
    private String unit;

    private LocalDate createdAt;
    private LocalDate updatedAt;
}