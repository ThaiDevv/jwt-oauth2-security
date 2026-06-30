package com.attt.secure.controller;

import com.attt.secure.config.RsaKeyConfig;
import com.attt.secure.dto.LoginRequest;
import com.attt.secure.dto.RegisterRequest;
import com.attt.secure.service.AuthService;
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

    /** POST /api/auth/login */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var user = authService.findUser(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Sai tài khoản hoặc mật khẩu"));
        }

        // Ký JWT bằng RSA private key, thuật toán RS256
        String token = Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role",   user.getRole())
            .claim("userId", user.getId())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L)) // 1 hour
            .signWith(rsaKeyConfig.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();

        log.info("🔑 [SECURE] Login OK | user={} | role={} | alg=RS256", user.getUsername(), user.getRole());

        return ResponseEntity.ok(Map.of(
            "success",    true,
            "token",      token,
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "user",       Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
            "serverType", "secure",
            "algorithm",  "RS256"
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
            RegisterRequest reg = new RegisterRequest();
            reg.setUsername(username);
            reg.setPassword(UUID.randomUUID().toString());
            reg.setRole("user");
            user = authService.register(reg);
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

        log.info("🔐 [SECURE] Google OAuth2 Login OK | user={} | role={} | alg=RS256", user.getUsername(), user.getRole());

        return ResponseEntity.ok(Map.of(
            "success",    true,
            "token",      token,
            "publicKey",  rsaKeyConfig.getPublicKeyPem(),
            "user",       Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()),
            "serverType", "secure",
            "algorithm",  "RS256"
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
