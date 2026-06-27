package com.ailogis.api.entity;

import com.ailogis.api.enums.Gender;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_company")
    @ToString.Exclude
    private Company company;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;

    private String fullName;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private String hashTaxCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ai_subscription")
    private AiSubscriptionTier aiTier;
}