package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "certification_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificationType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private LocalDate updateDate;
    private String lawReferences;
    private String pdfLink;
    @Column(columnDefinition = "TEXT")
    private String description;
}