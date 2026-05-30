package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "price_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_section", nullable = false)
    @ToString.Exclude
    private WarehouseSection section;

    private String label; // VD: "Thuê tháng", "Thuê ngày"
    private Double value; // VD: 200000
    private String unit; // VD: "VND"
    private String areaUnit; // VD: "m3", "tấn"
}