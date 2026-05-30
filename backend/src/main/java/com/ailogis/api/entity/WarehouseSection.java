package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warehouse_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse", nullable = false)
    @ToString.Exclude // Tránh lỗi đệ quy khi in log
    private Warehouse warehouse;

    private Integer sector; // Số thứ tự hoặc mã phòng (VD: 1, 2, 3)

    private Double totalCapacity;
    private Double availableCapacity;

    private Double tempMin;
    private Double tempMax;
    private Double humidity; // Độ ẩm

    private Boolean hasCertification;

    // 1 Phòng có thể có nhiều mức giá (VD: Giá theo tháng, giá theo tuần)
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PriceTier> priceTiers = new ArrayList<>();
}