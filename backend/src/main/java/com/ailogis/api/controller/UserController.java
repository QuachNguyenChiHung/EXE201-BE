package com.ailogis.api.controller;

import com.ailogis.api.dto.*;
import com.ailogis.api.entity.User;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.EmployeeService;
import com.ailogis.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final EmployeeService employeeService;
    private final UserService userService;

    // GET /api/users?keyword=...
    @GetMapping
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(employeeService.searchUsers(keyword, role, PageRequest.of(page, size)));
    }

    // POST /api/users
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserCreateUpdateDTO dto) {
        return ResponseEntity.ok(employeeService.createEmployee(dto));
    }

    // PATCH /api/users/:userID
    @PatchMapping("/{userID}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long userID,
            @RequestBody UserCreateUpdateDTO dto) {
        return ResponseEntity.ok(employeeService.updateUser(userID, dto));
    }

    // BAN/UNBAN USER
    @PatchMapping("/{userID}/status")
    public ResponseEntity<UserDTO> updateUserStatus(
            @PathVariable Long userID,
            @RequestBody Map<String, String> statusMap) { // Nhận json: {"status": "INACTIVE"}
        String newStatus = statusMap.get("status");
        return ResponseEntity.ok(employeeService.updateUserStatus(userID, newStatus));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.findByIdWithCompany(userDetails.getUser().getId());
        CompanyResponseDTO companyDTO = user.getCompany() != null ? new CompanyResponseDTO(user.getCompany().getId(),
                user.getCompany().getCompanyName(), user.getCompany().getCompanyTaxCode()) : null;

        return ResponseEntity.ok(new UserProfileDTO(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRole().name(), user.getStatus().name(),
                user.getDateOfBirth(), user.getGender() != null ? user.getGender().name() : null,
                companyDTO));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileDTO> updateMyProfile(
            @RequestBody UserProfileUpdateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(userService.updateMyProfile(userDetails.getUser().getId(), dto));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileDTO> uploadMyAvatar(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(userService.uploadAvatar(userDetails.getUser().getId(), file));
    }
}