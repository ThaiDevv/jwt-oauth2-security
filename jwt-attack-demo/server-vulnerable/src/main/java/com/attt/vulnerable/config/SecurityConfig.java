package com.attt.vulnerable.config;

import com.attt.vulnerable.filter.VulnerableJwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Security Config cho server-vulnerable.
 *
 * Hai security chains:
 * 1. /oauth2/** → Session-based (IF_REQUIRED) để hỗ trợ OAuth2 CSRF demo
 * 2. /api/**    → STATELESS (JWT Bearer token) cho REST API
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RsaKeyConfig rsaKeyConfig;

    /**
     * Security chain cho OAuth2 server-side flow (Thymeleaf + Session).
     * Priority cao hơn (order = 1) → xử lý trước.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            // Chỉ áp dụng cho /oauth2/**
            .securityMatcher("/oauth2/**")
            .csrf(csrf -> csrf.disable()) // Disable CSRF của Spring để demo lỗ hổng OAuth state
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // ⚠️ Dùng IF_REQUIRED để có Session cho OAuth2 flow
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Cho phép tất cả /oauth2/** (login page, callback, profile)
            );
        return http.build();
    }

    /**
     * Security chain cho REST API (JWT Bearer token).
     */
    @Bean
    @org.springframework.core.annotation.Order(2)
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
                new VulnerableJwtFilter(rsaKeyConfig),
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
