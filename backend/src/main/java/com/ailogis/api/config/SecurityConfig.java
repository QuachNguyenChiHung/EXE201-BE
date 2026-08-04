package com.ailogis.api.config;

import com.ailogis.api.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // 1. PUBLIC API
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/warehouses/**").permitAll()
                        .requestMatchers("/api/ai/filter-meta").permitAll()
                        .requestMatchers("/api/payment/**").permitAll()
                        .requestMatchers("/api/ws/**").permitAll()

                        // 2. API CÁ NHÂN
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()

                        // 3. API DÀNH RIÊNG CHO QUẢN TRỊ VIÊN (EMPLOYEE)
                        .requestMatchers("/api/users/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/employees/**").hasRole("EMPLOYEE")

                        // 4. API DÀNH RIÊNG CHO CHỦ KHO & KHÁCH THUÊ
                        .requestMatchers("/api/owners/**").hasRole("OWNER")
                        .requestMatchers("/api/renters/**").hasRole("RENTER")

                        // 5. Bắt buộc đăng nhập cho các request còn sót lại
                        .requestMatchers("/api/requests/**").authenticated()
                        .requestMatchers("/api/contracts/**").authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://www.logicha.io.vn",
                "https://logicha.io.vn"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}