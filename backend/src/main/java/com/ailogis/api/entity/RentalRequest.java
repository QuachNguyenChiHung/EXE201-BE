package com.ailogis.api.entity;

import com.ailogis.api.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rental_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_renter", nullable = false)
    private User renter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse", nullable = false)
    private Warehouse warehouse;

    private String cargoDescription; // Mô tả hàng hóa
    private String otherDetail;      // Ghi chú thêm

    private Integer duration;        // Số thời gian thuê (VD: 6)
    private String durationUnit;     // Đơn vị (VD: "Tháng", "Ngày")

    private String renterRejectionReason; // Lý do hủy/từ chối

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    // --- QUAN HỆ VỚI CHI TIẾT ---
    @OneToMany(mappedBy = "rentRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RentRequestDetail> details = new ArrayList<>();
}