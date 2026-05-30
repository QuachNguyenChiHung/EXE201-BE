package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouse_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_warehouse", nullable = false)
    @ToString.Exclude
    private Warehouse warehouse;

    private String imageUrl; // URL do FileStorageService trả về
    private Boolean isThumbnail; // Đánh dấu ảnh nào là ảnh bìa
    private Integer displayOrder; // Thứ tự hiển thị ảnh trên UI
}