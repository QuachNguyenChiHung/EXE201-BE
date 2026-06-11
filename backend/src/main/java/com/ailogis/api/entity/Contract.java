package com.ailogis.api.entity;

import com.ailogis.api.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "contracts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_owner", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_renter", nullable = false)
    private User renter;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rent_request", nullable = false, unique = true)
    private RentalRequest request;

    private String cargoDescription;
    private LocalDate startAt;
    private LocalDate endAt;

    @Column(columnDefinition = "TEXT")
    private String paymentTerm;  // Điều khoản thanh toán

    @Column(columnDefinition = "TEXT")
    private String penaltyClause;// Điều khoản phạt

    @Column(columnDefinition = "TEXT")
    private String specialTerm;  // Điều khoản đặc biệt

    private String cancelReason;

    // --- THÔNG TIN PHÁP LÝ (BẢN CHỤP BẤT BIẾN) ---
    private String ownerLegalName;
    private String ownerTaxCode; // Mã số thuế công ty chủ kho
    private String ownerEmail;
    private String ownerPhone;
    @Column(columnDefinition = "TEXT")
    private String ownerAddress;

    private String renterLegalName;
    private String renterTaxCode; // Mã số thuế công ty khách thuê
    private String renterEmail;
    private String renterPhone;
    @Column(columnDefinition = "TEXT")
    private String renterAddress;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ContractStatus status = ContractStatus.ACTIVE;
}