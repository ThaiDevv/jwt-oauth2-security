package com.attt.vulnerable.controller;

import com.attt.vulnerable.config.RsaKeyConfig;
import com.attt.vulnerable.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RsaKeyConfig rsaKeyConfig;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    /** GET /api/auth/config — Trả về cấu hình Google Client ID public */
    @GetMapping("/auth/config")
    public ResponseEntity<?> getAuthConfig() {
        return ResponseEntity.ok(Map.of(
            "googleClientId", googleClientId
        ));
    }

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

    /** POST /api/auth/oauth2-login */
    @PostMapping("/auth/oauth2-login")
    public ResponseEntity<?> oauth2Login(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing code"));
        }

        Map<String, Object> userInfo = fetchOAuthUserInfo(code);
        if (userInfo == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Không thể xác thực với Google OAuth2 Server"));
        }

        // Dùng email hoặc sub của Google để định danh người dùng
        String username = (String) userInfo.get("email");
        if (username == null || username.isEmpty()) {
            username = (String) userInfo.get("sub");
        }
        if (username == null || username.isEmpty()) {
            username = "google_user_" + UUID.randomUUID().toString().substring(0, 8);
        }

        var user = authService.findUser(username);
        if (user == null) {
            String randomPassword = UUID.randomUUID().toString();
            user = authService.register(username, randomPassword, "user");
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

        log.info("🔐 [VULNERABLE] Google OAuth2 Login OK: {} ({})", user.getUsername(), user.getRole());
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

    private Map<String, Object> fetchOAuthUserInfo(String code) {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("code", code);
            map.add("client_id", googleClientId);
            map.add("client_secret", googleClientSecret);
            map.add("grant_type", "authorization_code");
            map.add("redirect_uri", "http://localhost:3000/callback");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            Map response = restTemplate.postForObject(tokenUrl, request, Map.class);
            if (response != null && response.containsKey("access_token")) {
                String accessToken = (String) response.get("access_token");

                HttpHeaders uiHeaders = new HttpHeaders();
                uiHeaders.setBearerAuth(accessToken);
                HttpEntity<Void> uiRequest = new HttpEntity<>(uiHeaders);

                ResponseEntity<Map> uiResponse = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    uiRequest,
                    Map.class
                );
                if (uiResponse.getStatusCode().is2xxSuccessful() && uiResponse.getBody() != null) {
                    return uiResponse.getBody();
                }
            }
        } catch (Exception e) {
            log.error("Failed to connect to Google OAuth Server or exchange code: {}", e.getMessage(), e);
        }
        return null;
    }
}
