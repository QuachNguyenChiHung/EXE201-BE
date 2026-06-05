package com.ailogis.api.controller;

import com.ailogis.api.dto.UserCreateUpdateDTO;
import com.ailogis.api.dto.UserDTO;
import com.ailogis.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final EmployeeService employeeService;

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
}