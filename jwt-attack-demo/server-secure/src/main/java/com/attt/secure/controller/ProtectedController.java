package com.attt.secure.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller bảo vệ cho server-secure.
 * /api/profile — bất kỳ user đã xác thực
 * /api/admin   — chỉ ADMIN (kết hợp @PreAuthorize)
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ProtectedController {

    @GetMapping("/profile")
    public ResponseEntity<?> profile(Authentication auth) {
        Claims claims = (Claims) auth.getDetails();
        log.info("👤 [SECURE] Profile access: {}", auth.getName());
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "username",   auth.getName(),
            "role",       claims.get("role"),
            "userId",     claims.get("userId"),
            "serverType", "secure"
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> admin(Authentication auth) {
        Claims claims = (Claims) auth.getDetails();
        log.info("✅ [SECURE] Admin access granted to: {}", auth.getName());
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "message",    "✅ ADMIN PANEL — Secure Server",
            "secret",     "FLAG{this_server_is_protected_against_cve_2015_9235}",
            "user",       auth.getName(),
            "role",       claims.get("role"),
            "serverType", "secure",
            "note",       "Bạn có quyền admin hợp lệ. Server này KHÔNG bị CVE-2015-9235 ảnh hưởng."
        ));
    }
}
