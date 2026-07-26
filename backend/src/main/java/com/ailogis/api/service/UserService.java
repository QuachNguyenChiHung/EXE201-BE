package com.ailogis.api.service;

import com.ailogis.api.dto.CompanyResponseDTO;
import com.ailogis.api.dto.UserProfileDTO;
import com.ailogis.api.dto.UserProfileUpdateDTO;
import com.ailogis.api.entity.Company;
import com.ailogis.api.entity.User;
import com.ailogis.api.enums.Gender;
import com.ailogis.api.repository.CompanyRepository;
import com.ailogis.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public User findByIdWithCompany(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
    }

    @Transactional
    public UserProfileDTO updateMyProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = userRepository.findByIdWithCompany(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (dto.fullName() != null)
            user.setFullName(dto.fullName());
        if (dto.phone() != null)
            user.setPhone(dto.phone());
        if (dto.dateOfBirth() != null)
            user.setDateOfBirth(dto.dateOfBirth());
        if (dto.gender() != null)
            user.setGender(Gender.valueOf(dto.gender()));
        if (dto.companyName() != null || dto.companyTaxCode() != null) {
            Company company = user.getCompany();
            boolean isNew = (company == null);
            if (isNew) {
                company = new Company();
                user.setCompany(company);
            }
            if (dto.companyName() != null)
                company.setCompanyName(dto.companyName());
            if (dto.companyTaxCode() != null)
                company.setCompanyTaxCode(dto.companyTaxCode());
            if (isNew)
                company.getUsers().add(user);
            companyRepository.save(company); // persist first so company.id is available
            user.setCompany(company); // ensure user -> company FK is set
        }

        User updatedUser = userRepository.save(user);

        CompanyResponseDTO companyDTO = updatedUser.getCompany() != null
                ? new CompanyResponseDTO(updatedUser.getCompany().getId(), updatedUser.getCompany().getCompanyName(),
                        updatedUser.getCompany().getCompanyTaxCode())
                : null;

        return new UserProfileDTO(
                updatedUser.getId(), updatedUser.getEmail(), updatedUser.getFullName(), updatedUser.getPhone(),
                updatedUser.getAvatarUrl(), updatedUser.getRole().name(), updatedUser.getStatus().name(),
                updatedUser.getDateOfBirth(), updatedUser.getGender() != null ? updatedUser.getGender().name() : null,
                companyDTO);
    }

    @Transactional
    public UserProfileDTO uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findByIdWithCompany(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            fileStorageService.deleteFile(user.getAvatarUrl());
        }

        String newAvatarUrl = fileStorageService.storeFile(file, "avatars");
        user.setAvatarUrl(newAvatarUrl);

        User updatedUser = userRepository.save(user);

        CompanyResponseDTO companyDTO = updatedUser.getCompany() != null
                ? new CompanyResponseDTO(updatedUser.getCompany().getId(), updatedUser.getCompany().getCompanyName(),
                        updatedUser.getCompany().getCompanyTaxCode())
                : null;

        return new UserProfileDTO(
                updatedUser.getId(), updatedUser.getEmail(), updatedUser.getFullName(), updatedUser.getPhone(),
                updatedUser.getAvatarUrl(), updatedUser.getRole().name(), updatedUser.getStatus().name(),
                updatedUser.getDateOfBirth(), updatedUser.getGender() != null ? updatedUser.getGender().name() : null,
                companyDTO);
    }
}