package com.attt.secure.controller;

import com.attt.secure.config.RsaKeyConfig;
import com.attt.secure.dto.RegisterRequest;
import com.attt.secure.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * ✅ SECURE OAuth2 Controller — OAuth Login CSRF Prevention
 * ============================================================
 *
 * BẢO VỆ: State Parameter Validation (RFC 6749 §10.12)
 *
 * OAuth2 Authorization Code Flow AN TOÀN:
 *   1. Server sinh `state` ngẫu nhiên bằng SecureRandom
 *   2. Server lưu `state` vào HttpSession (server-side)
 *   3. Server gắn `state` vào Authorization URL
 *   4. Google redirect về callback với `code` + `state`
 *   5. Server SO SÁNH `state` callback với `state` trong session
 *   6. Nếu khác hoặc thiếu → HTTP 400 "Invalid OAuth State" → TỪ CHỐI
 *   7. Nếu khớp → xóa state khỏi session (one-time use) → tiếp tục
 *
 * Cơ chế này ngăn chặn tấn công CSRF:
 *   - Mỗi login flow có state duy nhất gắn với session của user đó
 *   - Attacker không thể biết state của session Victim
 *   - → Link callback của Attacker sẽ bị từ chối với "Invalid OAuth State"
 */
@Slf4j
@Controller
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final AuthService authService;
    private final RsaKeyConfig rsaKeyConfig;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    // Redirect URI phải khớp với cấu hình trên Google Cloud Console
    private static final String REDIRECT_URI = "http://localhost:4001/oauth2/callback";

    // Session key để lưu/lấy state
    private static final String SESSION_OAUTH_STATE = "oauth2_state";

    /**
     * GET /oauth2/login
     * Trang đăng nhập Google OAuth2 (server-side).
     * ✅ AN TOÀN: Sinh state bằng SecureRandom, lưu vào HttpSession.
     */
    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {

        // ============================================================
        // BƯỚC 1: Sinh state ngẫu nhiên bằng SecureRandom
        // SecureRandom đảm bảo entropy cao, không thể đoán được
        // ============================================================
        String state = generateSecureState();

        // ============================================================
        // BƯỚC 2: Lưu state vào HttpSession (server-side, không lộ ra client)
        // Session được bind với browser của user → Attacker không thể biết!
        // ============================================================
        session.setAttribute(SESSION_OAUTH_STATE, state);

        String authUrl = buildAuthorizationUrl(state);

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ [SECURE] BƯỚC 1: Authorization Request");
        log.info("✅ Generated state = '{}'", state);
        log.info("✅ Saved to session[{}] = '{}'", SESSION_OAUTH_STATE, state);
        log.info("✅ Authorization URL: {}", authUrl);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        model.addAttribute("authUrl",    authUrl);
        model.addAttribute("state",      state);
        model.addAttribute("sessionId",  session.getId().substring(0, 8) + "...");
        model.addAttribute("serverType", "SECURE");
        model.addAttribute("port",       4001);
        return "redirect:" + authUrl;
    }

    /**
     * GET /oauth2/callback
     * Google redirect về đây sau khi user đăng nhập.
     *
     * ✅ AN TOÀN: Validate state từ callback === state từ session
     * Nếu không khớp → HTTP 400 "Invalid OAuth State" → Từ chối.
     */
    @GetMapping("/callback")
    public String callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session,
            Model model) {

        // ============================================================
        // BƯỚC 2: Lấy state đã lưu từ session
        // ============================================================
        String savedState = (String) session.getAttribute(SESSION_OAUTH_STATE);

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ [SECURE] BƯỚC 2: Callback nhận được");
        log.info("✅ code          = {}", code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null");
        log.info("✅ callback state = '{}'", state);
        log.info("✅ session state  = '{}'", savedState);

        // Xử lý lỗi từ Google
        if (error != null) {
            log.warn("⚠️  [SECURE] Google trả về lỗi: {}", error);
            model.addAttribute("error", "Google trả về lỗi: " + error);
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        // ============================================================
        // BƯỚC 3: VALIDATE STATE — Điểm cốt lõi chống CSRF!
        // ============================================================
        if (savedState == null) {
            log.warn("🚫 [SECURE] STATE VALIDATION FAILED: Không có state trong session!");
            log.warn("🚫 → Có thể là CSRF attack hoặc session đã hết hạn");
            log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            model.addAttribute("error",
                "Invalid OAuth State: Không tìm thấy state trong session. " +
                "Đây có thể là tấn công CSRF hoặc session đã hết hạn.");
            model.addAttribute("csrfDetected", true);
            model.addAttribute("callbackState", state);
            model.addAttribute("sessionState",  "(null — không có trong session)");
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        if (state == null || state.isEmpty()) {
            log.warn("🚫 [SECURE] STATE VALIDATION FAILED: Callback không có state!");
            log.warn("🚫 callback state = null/empty, session state = '{}'", savedState);
            log.warn("🚫 → CSRF ATTACK DETECTED! Từ chối yêu cầu.");
            log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            model.addAttribute("error", "Invalid OAuth State: Callback thiếu tham số state.");
            model.addAttribute("csrfDetected", true);
            model.addAttribute("callbackState", "(trống)");
            model.addAttribute("sessionState",  savedState);
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        if (!savedState.equals(state)) {
            log.warn("🚫 [SECURE] STATE VALIDATION FAILED: State KHÔNG KHỚP!");
            log.warn("🚫 callback state = '{}', session state = '{}'", state, savedState);
            log.warn("🚫 → CSRF ATTACK DETECTED! Từ chối yêu cầu.");
            log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            model.addAttribute("error", "Invalid OAuth State: State không khớp. Tấn công CSRF bị phát hiện và chặn!");
            model.addAttribute("csrfDetected", true);
            model.addAttribute("callbackState", state);
            model.addAttribute("sessionState",  savedState);
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        // ✅ State khớp! Xóa khỏi session (one-time use — chống replay attack)
        session.removeAttribute(SESSION_OAUTH_STATE);
        log.info("✅ [SECURE] BƯỚC 3: STATE VALIDATION PASSED! '{}' === '{}'", state, savedState);
        log.info("✅ State đã bị xóa khỏi session (one-time use)");

        if (code == null || code.isEmpty()) {
            model.addAttribute("error", "Không có authorization code trong callback");
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        // ============================================================
        // BƯỚC 4: Đổi authorization_code lấy access token
        // ============================================================
        log.info("✅ [SECURE] BƯỚC 4: Đổi code lấy access token...");
        Map<String, Object> tokenResponse = exchangeCodeForToken(code);
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            model.addAttribute("error", "Không thể đổi code lấy access token. Code đã hết hạn hoặc sai redirect_uri.");
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        String accessToken = (String) tokenResponse.get("access_token");
        log.info("✅ [SECURE] Access token nhận được: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));

        // ============================================================
        // BƯỚC 5: Lấy thông tin người dùng từ Google
        // ============================================================
        log.info("✅ [SECURE] BƯỚC 5: Lấy user info từ Google...");
        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        if (userInfo == null) {
            model.addAttribute("error", "Không thể lấy thông tin người dùng từ Google");
            model.addAttribute("serverType", "SECURE");
            return "oauth2/error";
        }

        String email    = (String) userInfo.get("email");
        String name     = (String) userInfo.get("name");
        String picture  = (String) userInfo.get("picture");
        String googleId = (String) userInfo.get("sub");

        log.info("✅ [SECURE] User Info: email={}, name={}, sub={}", email, name, googleId);

        // ============================================================
        // BƯỚC 6: Tạo/tìm user trong DB và tạo session
        // ============================================================
        String username = email != null ? email : "google_" + googleId;
        var user = authService.findUser(username);
        if (user == null) {
            RegisterRequest reg = new RegisterRequest();
            reg.setUsername(username);
            reg.setPassword(UUID.randomUUID().toString());
            reg.setRole("user");
            user = authService.register(reg);
            log.info("✅ [SECURE] Tạo user mới: {}", username);
        }

        // Tạo JWT token
        String jwtToken = Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role",     user.getRole())
            .claim("userId",   user.getId())
            .claim("email",    email)
            .claim("name",     name)
            .claim("googleId", googleId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
            .signWith(rsaKeyConfig.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();

        // Lưu vào session
        session.setAttribute("jwt_token",       jwtToken);
        session.setAttribute("user_email",      email);
        session.setAttribute("user_name",       name);
        session.setAttribute("user_picture",    picture);
        session.setAttribute("user_google_id",  googleId);
        session.setAttribute("login_via",       "google_oauth2");

        log.info("✅ [SECURE] BƯỚC 6: LOGIN THÀNH CÔNG!");
        log.info("✅ Email: {}, GoogleID: {}", email, googleId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return "redirect:/oauth2/profile";
    }

    /**
     * GET /oauth2/profile
     * Hiển thị thông tin user sau khi đăng nhập OAuth2.
     */
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        String token = (String) session.getAttribute("jwt_token");
        if (token == null) {
            return "redirect:/oauth2/login";
        }

        model.addAttribute("email",      session.getAttribute("user_email"));
        model.addAttribute("name",       session.getAttribute("user_name"));
        model.addAttribute("picture",    session.getAttribute("user_picture"));
        model.addAttribute("googleId",   session.getAttribute("user_google_id"));
        model.addAttribute("serverType", "SECURE");
        model.addAttribute("port",       4001);
        model.addAttribute("loginVia",   session.getAttribute("login_via"));

        log.info("✅ [SECURE] Profile page truy cập bởi: {}", session.getAttribute("user_email"));
        return "oauth2/profile";
    }

    /**
     * GET /oauth2/logout
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        log.info("🚪 [SECURE] Đã đăng xuất");
        return "redirect:/oauth2/login";
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Sinh state ngẫu nhiên bằng SecureRandom — đủ entropy để không thể đoán.
     * 128 bits = 16 bytes → Base64URL encode → ~22 ký tự.
     */
    private String generateSecureState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Xây dựng Google Authorization URL, bao gồm state để chống CSRF.
     */
    private String buildAuthorizationUrl(String state) {
        String scope = URLEncoder.encode("openid profile email", StandardCharsets.UTF_8);
        String redirectUri = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8);
        return "https://accounts.google.com/o/oauth2/v2/auth" +
               "?client_id=" + googleClientId +
               "&redirect_uri=" + redirectUri +
               "&response_type=code" +
               "&scope=" + scope +
               "&access_type=offline" +
               "&state=" + state; // ✅ Gắn state vào URL!
    }

    /**
     * Bước 4: Đổi authorization_code lấy access_token từ Google.
     */
    private Map<String, Object> exchangeCodeForToken(String code) {
        RestTemplate rest = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code",          code);
            params.add("client_id",     googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri",  REDIRECT_URI);
            params.add("grant_type",    "authorization_code");

            HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(params, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = rest.postForObject(
                "https://oauth2.googleapis.com/token", req, Map.class);
            return response;
        } catch (Exception e) {
            log.error("❌ [SECURE] Lỗi khi đổi code lấy token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Bước 5: Lấy thông tin người dùng từ Google UserInfo endpoint.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        RestTemplate rest = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<Map> response = rest.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );
            return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
        } catch (Exception e) {
            log.error("❌ [SECURE] Lỗi khi lấy user info: {}", e.getMessage());
            return null;
        }
    }
}
