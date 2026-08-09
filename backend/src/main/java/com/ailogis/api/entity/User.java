package com.ailogis.api.entity;

import com.ailogis.api.enums.Gender;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    /** Số lần đăng nhập thất bại liên tiếp; reset về 0 khi đăng nhập thành công. */
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0;

    /** Thời điểm tài khoản được mở khóa lại sau khi bị khóa do nhập sai mật khẩu quá nhiều lần. */
    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    /**
     * Thời điểm của lần đăng nhập thất bại gần nhất. Dùng cho idle reset: nếu
     * lần thử hiện tại cách lần sai trước hơn {@code app.login.lock-duration-minutes}
     * phút, bộ đếm được reset về 0 và lần thử hiện tại không bị tính.
     */
    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;
}