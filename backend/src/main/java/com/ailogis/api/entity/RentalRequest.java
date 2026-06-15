package com.ailogis.api.entity;

import com.ailogis.api.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
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

    // Mô tả hàng hóa
    private String cargoDescription;

    // Ghi chú thêm
    private String otherDetail;

    // Số thời gian thuê (VD: 6)
    private Integer duration;

    // Đơn vị (VD: "Tháng", "Ngày")
    private String durationUnit;

    private LocalDate startDate;
    private LocalDate endDate;

    // Mức giá mới mà Owner đề xuất lại cho Renter nếu không đồng ý với giá gốc
    private Double offeredPrice;

    // Mức giá mà Renter mong muốn lúc mới tạo đơn
    @Column(name = "renter_offered_price")
    private Double renterOfferedPrice;

    // Đơn vị tính cho mức giá đề xuất (VD: "VND/m3", "VND/Tấn")
    private String unit;

    // Lời nhắn hoặc ghi chú của Chủ kho gửi cho Khách thuê khi duyệt, từ chối hoặc thương lượng lại giá
    @Column(columnDefinition = "TEXT")
    private String ownerNote;

    // Lý do hủy/từ chối của renter
    private String renterRejectionReason;

    // Lý do hủy/từ chối của owner
    private String rejectionReason;

    @CreationTimestamp
    private LocalDate submitAt;

    @UpdateTimestamp
    private LocalDate updatedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    // --- QUAN HỆ VỚI CHI TIẾT ---
    @OneToMany(mappedBy = "rentRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RentRequestDetail> details = new ArrayList<>();
}