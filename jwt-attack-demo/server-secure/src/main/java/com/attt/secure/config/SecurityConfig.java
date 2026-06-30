package com.attt.secure.config;

import com.attt.secure.filter.SecureJwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ✅ Security Config AN TOÀN cho server-secure.
 *
 * Hai security chains:
 * 1. /oauth2/** → Session-based (IF_REQUIRED) để lưu state cho OAuth2 CSRF prevention
 * 2. /api/**    → STATELESS (JWT Bearer) cho REST API với algorithm whitelist
 *
 * Lý do cần Session cho /oauth2/**:
 *   - State phải được lưu server-side (trong session), không phải client-side
 *   - Client không thể giả mạo session server-side → an toàn chống CSRF
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RsaKeyConfig rsaKeyConfig;

    /**
     * Security chain cho OAuth2 server-side flow (Thymeleaf + Session).
     * Priority cao hơn (order = 1) → xử lý trước.
     *
     * ✅ Session IF_REQUIRED: Cần thiết để lưu oauth_state vào session server-side.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/oauth2/**")
            .csrf(csrf -> csrf.disable()) // CSRF của Spring tắt — ta tự implement OAuth state check
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // ✅ IF_REQUIRED: Tạo session khi cần → lưu oauth_state server-side
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }

    /**
     * Security chain cho REST API (JWT Bearer token).
     * ✅ Chỉ chấp nhận RS256 — bảo vệ chống CVE-2015-9235.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**", "/health")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/public-key", "/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new SecureJwtFilter(rsaKeyConfig),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:4001",
            "http://localhost:4002",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "http://127.0.0.1:4001",
            "http://127.0.0.1:4002"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
