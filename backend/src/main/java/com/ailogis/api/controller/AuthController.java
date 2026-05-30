package com.ailogis.api.controller;

import com.ailogis.api.dto.LoginRequestDTO;
import com.ailogis.api.dto.LoginResponseDTO;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> authenticateUser(@RequestBody LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateToken(userDetails);

        return ResponseEntity.ok(new LoginResponseDTO(
                jwtToken,
                "Bearer",
                userDetails.getUsername(),
                userDetails.getUser().getRole().name()
        ));
    }
}