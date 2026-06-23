package com.attt.oauth.controller;

import com.attt.oauth.service.AuthCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TokenController — Đổi auth code lấy access token, xem userinfo.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TokenController {

    private final AuthCodeService authCodeService;

    /** GET /health */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "server", "oauth", "port", 4003));
    }

    /**
     * POST /token — Đổi authorization code lấy access token
     * Hỗ trợ cả application/x-www-form-urlencoded và JSON
     */
    @PostMapping(value = "/token", consumes = {"application/x-www-form-urlencoded", "application/json"})
    public ResponseEntity<?> token(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String client_id,
        @RequestParam(required = false) String grant_type,
        @RequestParam(required = false) String redirect_uri,
        @RequestBody(required = false) Map<String, String> body
    ) {
        // Lấy params từ body JSON nếu không có form params
        if (code == null && body != null) {
            code        = body.get("code");
            client_id   = body.get("client_id");
            grant_type  = body.get("grant_type");
            redirect_uri = body.get("redirect_uri");
        }

        log.info("🔑 [OAUTH] POST /token | code={} | client_id={}", code, client_id);

        if (!"authorization_code".equals(grant_type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        String accessToken = authCodeService.exchangeCode(code);
        if (accessToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant", "detail", "Code không hợp lệ hoặc đã được sử dụng"));
        }

        return ResponseEntity.ok(Map.of(
            "access_token", accessToken,
            "token_type",   "Bearer",
            "expires_in",   3600,
            "scope",        "openid profile email"
        ));
    }

    /** GET /userinfo — Trả về user info */
    @GetMapping("/userinfo")
    public ResponseEntity<?> userinfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        var userInfo = authCodeService.getUserInfo(token);
        if (userInfo == null) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }
        log.info("👤 [OAUTH] UserInfo accessed for token: {}...", token.substring(0, 10));
        return ResponseEntity.ok(userInfo);
    }
}
