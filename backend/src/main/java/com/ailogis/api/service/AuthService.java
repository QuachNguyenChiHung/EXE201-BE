package com.ailogis.api.service;

import com.ailogis.api.dto.RegisterRequestDTO;
import com.ailogis.api.entity.Company;
import com.ailogis.api.entity.User;
import com.ailogis.api.enums.Role;
import com.ailogis.api.enums.UserStatus;
import com.ailogis.api.repository.CompanyRepository;
import com.ailogis.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public String registerUser(RegisterRequestDTO dto) {
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw new RuntimeException("Email này đã được sử dụng!");
        }

        // 1. Kiểm tra tính hợp lệ của Role
        Role requestedRole;
        try {
            requestedRole = Role.valueOf(dto.role().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Vai trò (Role) không hợp lệ!");
        }

        // 2. Chặn đứng hành vi tạo tài khoản nội bộ (EMPLOYEE) từ bên ngoài
        if (requestedRole == Role.EMPLOYEE) {
            throw new RuntimeException("Cảnh báo bảo mật: Không được phép đăng ký tài khoản Quản trị viên qua cổng public!");
        }

        Company company = null;
        if (dto.companyName() != null && !dto.companyName().isEmpty()) {
            company = Company.builder()
                    .companyName(dto.companyName())
                    .companyTaxCode(dto.companyTaxCode())
                    .build();
            company = companyRepository.save(company);
        }

        User newUser = User.builder()
                .email(dto.email())
                .password(dto.password())
                .fullName(dto.fullName())
                .phone(dto.phone())
                .role(requestedRole) // Sử dụng biến đã được verify
                .status(UserStatus.ACTIVE)
                .company(company)
                .build();

        userRepository.save(newUser);
        return "Đăng ký tài khoản thành công!";
    }
}