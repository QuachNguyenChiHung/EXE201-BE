package com.ailogis.api.controller;

import com.ailogis.api.dto.ForgotPasswordRequestDTO;
import com.ailogis.api.dto.ForgotPasswordResponseDTO;
import com.ailogis.api.dto.LoginRequestDTO;
import com.ailogis.api.dto.LoginResponseDTO;
import com.ailogis.api.dto.MessageResponseDTO;
import com.ailogis.api.dto.RegisterRequestDTO;
import com.ailogis.api.dto.ResetPasswordRequestDTO;
import com.ailogis.api.dto.VerifyOtpRequestDTO;
import com.ailogis.api.entity.UserSession;
import com.ailogis.api.repository.UserSessionRepository;
import com.ailogis.api.security.CustomUserDetails;
import com.ailogis.api.security.JwtUtils;
import com.ailogis.api.service.AuthService;
import com.ailogis.api.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> authenticateUser(@RequestBody LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateToken(userDetails);

        UserSession session = UserSession.builder()
                .user(userDetails.getUser())
                .loginAt(LocalDateTime.now())
                .loginDate(LocalDate.now())
                .build();
        userSessionRepository.save(session);

        return ResponseEntity.ok(new LoginResponseDTO(
                jwtToken,
                "Bearer",
                userDetails.getUsername(),
                userDetails.getUser().getRole().name()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequestDTO dto) {
        return ResponseEntity.ok(authService.registerUser(dto));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            userSessionRepository.findLatestActiveSession(userDetails.getUser().getId())
                    .ifPresent(session -> {
                        session.setLogoutAt(LocalDateTime.now());
                        userSessionRepository.save(session);
                    });
        }
        return ResponseEntity.ok("Đăng xuất thành công, session đã đóng!");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@RequestBody ForgotPasswordRequestDTO dto) {
        return ResponseEntity.ok(passwordResetService.requestOtp(dto.email()));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponseDTO> verifyOtp(@RequestBody VerifyOtpRequestDTO dto) {
        return ResponseEntity.ok(passwordResetService.verifyOtp(dto));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDTO> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        return ResponseEntity.ok(passwordResetService.resetPassword(dto));
    }
}