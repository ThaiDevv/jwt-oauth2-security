package com.attt.vulnerable.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class ProtectedController {

    @GetMapping("/profile")
    public ResponseEntity<?> profile(Authentication auth) {
        Claims claims = (Claims) auth.getDetails();
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "username",   auth.getName(),
            "role",       claims.get("role"),
            "serverType", "vulnerable",
            "warning",    "⚠️ Server này có lỗ hổng CVE-2015-9235"
        ));
    }

    /** Admin endpoint — bị bypass bởi CVE-2015-9235 */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> admin(Authentication auth) {
        Claims claims = (Claims) auth.getDetails();
        log.error("🚨 [VULNERABLE] Admin accessed by: {} — CÓ THỂ LÀ ATTACKER!", auth.getName());
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "message",    "🔓 ADMIN ACCESS GRANTED — Vulnerable Server",
            "flag",       "FLAG{jwt_alg_confusion_cve_2015_9235_pwned}",
            "user",       auth.getName(),
            "role",       claims.get("role"),
            "serverType", "vulnerable",
            "warning",    "Server này bị tấn công bởi JWT Algorithm Confusion Attack (CVE-2015-9235)!"
        ));
    }
}
