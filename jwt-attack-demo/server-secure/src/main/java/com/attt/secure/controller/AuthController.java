package com.attt.secure.controller;

import com.attt.secure.config.RsaKeyConfig;
import com.attt.secure.dto.LoginRequest;
import com.attt.secure.dto.RegisterRequest;
import com.attt.secure.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RsaKeyConfig rsaKeyConfig;
    private final PasswordEncoder passwordEncoder;

    /** GET /api/public-key — Trả về RSA public key PEM */
    @GetMapping("/public-key")
    public ResponseEntity<?> getPublicKey() {
        log.info("📤 [SECURE] GET /api/public-key");
        return ResponseEntity.ok(Map.of(
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "algorithm",  "RS256",
            "serverType", "secure",
            "port",       4001,
            "timestamp",  System.currentTimeMillis()
        ));
    }

    /** GET /health */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "server", "secure", "port", 4001));
    }

    /** POST /api/auth/register */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            var user = authService.register(req);
            log.info("📝 [SECURE] Registered: {} | role: {}", user.getUsername(), user.getRole());
            return ResponseEntity.ok(Map.of(
                "success",    true,
                "message",    "Đăng ký thành công",
                "user",       Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
                "serverType", "secure"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** POST /api/auth/login — Ký JWT bằng RS256 + trả về public key */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var user = authService.findUser(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("⚠️  [SECURE] Login failed for: {}", req.getUsername());
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Sai username hoặc password"));
        }

        // Ký JWT bằng RSA private key, thuật toán RS256
        String token = Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role",   user.getRole())
            .claim("userId", user.getId())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
            .signWith(rsaKeyConfig.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();

        log.info("🔐 [SECURE] Login OK | user={} | role={} | alg=RS256", user.getUsername(), user.getRole());

        return ResponseEntity.ok(Map.of(
            "success",    true,
            "token",      token,
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "user",       Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
            "serverType", "secure",
            "algorithm",  "RS256"
        ));
    }
}
