package com.ailogis.api.controller;

import com.ailogis.api.dto.CompanyResponseDTO;
import com.ailogis.api.dto.UserCreateUpdateDTO;
import com.ailogis.api.dto.UserDTO;
import com.ailogis.api.dto.UserProfileDTO;
import com.ailogis.api.entity.User;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.service.EmployeeService;
import com.ailogis.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final EmployeeService employeeService;
    private final UserService userService;

    // GET /api/users?keyword=...
    @GetMapping
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(employeeService.searchUsers(keyword));
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

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.findByIdWithCompany(userDetails.getUser().getId());
        CompanyResponseDTO companyDTO = user.getCompany() != null ?
                new CompanyResponseDTO(user.getCompany().getId(), user.getCompany().getCompanyName(), user.getCompany().getCompanyTaxCode()) : null;

        return ResponseEntity.ok(new UserProfileDTO(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRole().name(), user.getStatus().name(), companyDTO
        ));
    }
}