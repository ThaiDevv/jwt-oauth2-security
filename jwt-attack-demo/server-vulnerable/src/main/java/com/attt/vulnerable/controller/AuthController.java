package com.attt.vulnerable.controller;

import com.attt.vulnerable.config.RsaKeyConfig;
import com.attt.vulnerable.service.AuthService;
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

    @GetMapping("/public-key")
    public ResponseEntity<?> getPublicKey() {
        log.warn("⚠️  [VULNERABLE] GET /api/public-key — Public key exposed to anyone!");
        return ResponseEntity.ok(Map.of(
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "algorithm",  "RS256",
            "serverType", "vulnerable",
            "port",       4002,
            "warning",    "Public key exposed — vulnerable to CVE-2015-9235!",
            "timestamp",  System.currentTimeMillis()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "server", "vulnerable", "port", 4002));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> req) {
        try {
            var user = authService.register(req.get("username"), req.get("password"), req.get("role"));
            return ResponseEntity.ok(Map.of(
                "success", true,
                "user", Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
                "serverType", "vulnerable"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        var user = authService.findUser(req.get("username"));
        if (user == null || !passwordEncoder.matches(req.get("password"), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Sai username hoặc password"));
        }

        String token = Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role",   user.getRole())
            .claim("userId", user.getId())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
            .signWith(rsaKeyConfig.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();

        log.info("🔐 [VULNERABLE] Login OK: {} ({})", user.getUsername(), user.getRole());
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "token",      token,
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "user",       Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
            "serverType", "vulnerable",
            "algorithm",  "RS256",
            "warning",    "Server này có lỗ hổng CVE-2015-9235!"
        ));
    }
}
