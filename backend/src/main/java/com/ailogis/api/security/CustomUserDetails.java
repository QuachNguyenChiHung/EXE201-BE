package com.ailogis.api.security;

import com.ailogis.api.entity.User;
import com.ailogis.api.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private User user;

    // Phân quyền: Cấp cho User cái mác "ROLE_RENTER", "ROLE_OWNER" hoặc "ROLE_EMPLOYEE"
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    // Spring Security dùng Username để đăng nhập, trong dự án ta dùng Email
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // Các hàm kiểm tra trạng thái tài khoản
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Khóa tài khoản nếu status là INACTIVE hoặc đang trong thời gian khóa tạm
        // vì nhập sai mật khẩu quá nhiều lần.
        if (user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        if (user.getLockUntil() == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(user.getLockUntil());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}