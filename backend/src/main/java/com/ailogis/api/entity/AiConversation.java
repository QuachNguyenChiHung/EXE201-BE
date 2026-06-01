package com.ailogis.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "ai_conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT") // Lưu dạng chuỗi JSON
    private String criteria;

    @Column(columnDefinition = "TEXT") // Lưu dạng chuỗi JSON
    private String message;

    private Integer totalInputTokens;
    private Integer totalOutputTokens;

    private LocalDate createdAt;
    private LocalDate updatedAt;
}