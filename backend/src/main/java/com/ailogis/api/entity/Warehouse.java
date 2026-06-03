package com.ailogis.api.entity;

import com.ailogis.api.enums.WarehouseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warehouses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String locationAddressText;
    private String locationProvince;
    private String locationCommune;
    private Double locationLong;
    private Double locationLat;
    private String locationPostalCode;

    private Boolean isSponsor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sponsor_type")
    private SponsorTier sponsorType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WarehouseStatus status = WarehouseStatus.PENDING;

    @CreationTimestamp
    private LocalDate createdAt;

    @UpdateTimestamp
    private LocalDate updatedAt;

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WarehouseSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WarehouseImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CertificationSubmit> certificationSubmits = new ArrayList<>();
}