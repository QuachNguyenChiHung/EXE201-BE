package com.ailogis.api.service;

import com.ailogis.api.dto.ChangePasswordRequestDTO;
import com.ailogis.api.dto.MessageResponseDTO;
import com.ailogis.api.entity.User;
import com.ailogis.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeePasswordService {

    private final UserRepository userRepository;

    @Transactional
    public MessageResponseDTO changeOwnPassword(Long userId, ChangePasswordRequestDTO dto) {
        if (dto == null) {
            throw new RuntimeException("Thiếu dữ liệu mật khẩu!");
        }
        if (dto.currentPassword() == null || dto.currentPassword().isEmpty()) {
            throw new RuntimeException("Mật khẩu hiện tại không được để trống!");
        }
        if (dto.newPassword() == null || dto.newPassword().length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        // Plain-text comparison matches the existing NoOpPasswordEncoder convention.
        if (!dto.currentPassword().equals(user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng!");
        }

        if (dto.newPassword().equals(user.getPassword())) {
            throw new RuntimeException("Mật khẩu mới phải khác mật khẩu hiện tại!");
        }

        user.setPassword(dto.newPassword());
        userRepository.save(user);

        return new MessageResponseDTO("Đổi mật khẩu thành công!");
    }
}
