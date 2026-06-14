package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_buyer", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_subscription")
    private AiSubscriptionTier subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sponsor")
    private SponsorTier sponsor;

    private String status;
    private String type;

    private LocalDateTime createdAt;
    private LocalDateTime invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse")
    private Warehouse warehouse;

    private Double amount;
}