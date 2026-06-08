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
                .role(Role.valueOf(dto.role().toUpperCase()))
                .status(UserStatus.ACTIVE)
                .company(company)
                .build();

        userRepository.save(newUser);
        return "Đăng ký tài khoản thành công!";
    }
}