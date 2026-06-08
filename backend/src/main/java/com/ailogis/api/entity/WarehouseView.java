package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "views")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_view")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_renter", nullable = false)
    private User renter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse", nullable = false)
    private Warehouse warehouse;

    private LocalDate viewDate;
}