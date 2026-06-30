package com.attt.vulnerable.controller;

import com.attt.vulnerable.model.User;
import com.attt.vulnerable.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProtectedController {

    private final UserRepository userRepository;

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

    /**
     * GET /api/admin/all-users — Lấy danh sách toàn bộ user.
     * Endpoint admin bị bypass bởi CVE-2015-9235 (JWT Algorithm Confusion).
     * Demo: server-vulnerable (4002) chấp nhận forged token alg=HS256.
     */
    @GetMapping("/admin/all-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(Authentication auth) {
        List<User> users = userRepository.findAll();
        log.error("🚨 [VULNERABLE] /api/admin/all-users accessed by: {} — CÓ THỂ LÀ ATTACKER!", auth.getName());
        return ResponseEntity.ok(Map.of(
            "success",    true,
            "message",    "🔓 ADMIN — Danh sách toàn bộ users!",
            "users",      users.stream().map(u -> Map.of(
                              "id", u.getId(),
                              "username", u.getUsername(),
                              "role", u.getRole()
                          )).toList(),
            "serverType", "vulnerable",
            "warning",    "Server này bị tấn công! Forged JWT token được chấp nhận (CVE-2015-9235)."
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

