package com.ailogis.api.entity;

import com.ailogis.api.enums.VerifyStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "certification_submits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificationSubmit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse", nullable = false)
    @ToString.Exclude
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_type", nullable = false)
    private CertificationType type;

    private String link; // URL file PDF

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerifyStatus status = VerifyStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectReason;
}