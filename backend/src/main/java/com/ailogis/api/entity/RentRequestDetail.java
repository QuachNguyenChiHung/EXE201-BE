package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rent_request_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentRequestDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rent_request", nullable = false)
    @ToString.Exclude
    private RentalRequest rentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_section", nullable = false)
    private WarehouseSection section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_price_tier", nullable = false)
    private PriceTier priceTier;

    private Double rentedArea; // Khối lượng/Diện tích khách muốn thuê ở phòng này
    private String areaUnit;   // Đơn vị (VD: "m3", "tấn")
}