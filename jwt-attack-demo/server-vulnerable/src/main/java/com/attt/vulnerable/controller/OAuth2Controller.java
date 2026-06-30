package com.attt.vulnerable.controller;

import com.attt.vulnerable.config.RsaKeyConfig;
import com.attt.vulnerable.service.AuthService;
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
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * ⚠️  VULNERABLE OAuth2 Controller — OAuth Login CSRF Demo
 * ============================================================
 *
 * LỖ HỔNG: Missing State Validation (OAuth Login CSRF)
 *
 * OAuth2 Authorization Code Flow (RFC 6749 §4.1):
 *   1. Client → Authorization Server: Gửi request kèm `state`
 *   2. User đăng nhập tại Authorization Server (Google)
 *   3. Authorization Server → Client: Redirect về callback kèm `code` + `state`
 *   4. Client PHẢI kiểm tra `state` callback === `state` đã gửi
 *   5. Client → Resource Owner: Dùng `code` đổi lấy access_token
 *
 * Server này CỐ TÌNH vi phạm bước 4:
 *   - Không sinh state → không lưu state vào session
 *   - Callback chỉ cần `code` là đủ → BỎ QUA state hoàn toàn
 *   - → Kẻ tấn công có thể thực hiện CSRF: buộc Victim đăng nhập bằng tài khoản Attacker
 *
 * Kịch bản tấn công (OAuth Login CSRF / Account Binding):
 *   1. Attacker bắt đầu OAuth flow → lấy Authorization URL
 *   2. Attacker KHÔNG hoàn thành đăng nhập → lấy URL callback có `code`
 *   3. Attacker gửi link đó cho Victim
 *   4. Victim click → server xử lý `code` của Attacker
 *   5. Victim bị đăng nhập vào tài khoản Google của Attacker!
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
    private static final String REDIRECT_URI = "http://localhost:4002/oauth2/callback";

    /**
     * GET /oauth2/login
     * Trang đăng nhập Google OAuth2 (server-side).
     * ⚠️ LỖ HỔNG: Không sinh state, không lưu vào session!
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        // ============================================================
        // BƯỚC 1: Tạo Authorization Request
        // ============================================================
        // ⚠️ LỖ HỔNG: state = null → không có gì để validate sau này!
        // Đúng ra phải: String state = generateSecureState(); session.setAttribute("oauth_state", state);
        String state = ""; // Không sinh state → lỗ hổng!

        String authUrl = buildAuthorizationUrl(state);

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("⚠️  [VULNERABLE] BƯỚC 1: Authorization Request");
        log.warn("⚠️  Authorization URL: {}", authUrl);
        log.warn("⚠️  state = '{}' (KHÔNG SINH STATE — LỖ HỔNG!)", state);
        log.warn("⚠️  Session KHÔNG lưu state → callback sẽ không validate");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        model.addAttribute("authUrl", authUrl);
        model.addAttribute("serverType", "VULNERABLE");
        model.addAttribute("port", 4002);
        return "redirect:" + authUrl;
    }

    /**
     * GET /oauth2/callback
     * Google redirect về đây sau khi user đăng nhập.
     *
     * ⚠️ LỖ HỔNG: Chỉ cần `code`, hoàn toàn BỎ QUA `state`!
     * Attacker có thể gửi link callback này cho Victim → Victim đăng nhập bằng account Attacker.
     */
    @GetMapping("/callback")
    public String callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session,
            Model model) {

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("⚠️  [VULNERABLE] BƯỚC 2: Callback nhận code");
        log.warn("⚠️  code    = {}", code);
        log.warn("⚠️  state   = '{}' (KHÔNG VALIDATE — LỖ HỔNG!)", state);
        log.warn("⚠️  error   = {}", error);

        // Xử lý lỗi từ Google
        if (error != null) {
            model.addAttribute("error", "Google trả về lỗi: " + error);
            model.addAttribute("serverType", "VULNERABLE");
            return "oauth2/error";
        }

        if (code == null || code.isEmpty()) {
            model.addAttribute("error", "Không có authorization code trong callback");
            model.addAttribute("serverType", "VULNERABLE");
            return "oauth2/error";
        }

        // ============================================================
        // ⚠️ LỖ HỔNG: KHÔNG KIỂM TRA STATE!
        // Đúng ra phải làm:
        //   String savedState = (String) session.getAttribute("oauth_state");
        //   if (savedState == null || !savedState.equals(state)) {
        //       return "error 401: Invalid OAuth State";
        //   }
        //   session.removeAttribute("oauth_state"); // Dùng 1 lần rồi xóa
        // ============================================================
        log.warn("⚠️  [VULNERABLE] STATE VALIDATION: BỎ QUA HOÀN TOÀN!");
        log.warn("⚠️  Attacker có thể gửi link này cho Victim → CSRF thành công!");

        // ============================================================
        // BƯỚC 3: Đổi authorization_code lấy access token
        // ============================================================
        log.warn("⚠️  [VULNERABLE] BƯỚC 3: Đổi code lấy access token...");
        Map<String, Object> tokenResponse = exchangeCodeForToken(code);
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            model.addAttribute("error", "Không thể đổi code lấy access token. Code đã hết hạn hoặc sai redirect_uri.");
            model.addAttribute("serverType", "VULNERABLE");
            return "oauth2/error";
        }

        String accessToken = (String) tokenResponse.get("access_token");
        log.warn("⚠️  [VULNERABLE] Access token nhận được: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));

        // ============================================================
        // BƯỚC 4: Lấy thông tin người dùng từ Google
        // ============================================================
        log.warn("⚠️  [VULNERABLE] BƯỚC 4: Lấy user info từ Google...");
        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        if (userInfo == null) {
            model.addAttribute("error", "Không thể lấy thông tin người dùng từ Google");
            model.addAttribute("serverType", "VULNERABLE");
            return "oauth2/error";
        }

        String email      = (String) userInfo.get("email");
        String name       = (String) userInfo.get("name");
        String picture    = (String) userInfo.get("picture");
        String googleId   = (String) userInfo.get("sub");
        Boolean verified  = (Boolean) userInfo.getOrDefault("email_verified", false);

        log.warn("⚠️  [VULNERABLE] User Info: email={}, name={}, sub={}", email, name, googleId);

        // ============================================================
        // BƯỚC 5: Tạo/tìm user trong DB và tạo session
        // ============================================================
        String username = email != null ? email : "google_" + googleId;
        var user = authService.findUser(username);
        if (user == null) {
            user = authService.register(username, UUID.randomUUID().toString(), "user");
            log.warn("⚠️  [VULNERABLE] Tạo user mới: {}", username);
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
        session.setAttribute("jwt_token", jwtToken);
        session.setAttribute("user_email", email);
        session.setAttribute("user_name", name);
        session.setAttribute("user_picture", picture);
        session.setAttribute("user_google_id", googleId);
        session.setAttribute("login_via", "google_oauth2");

        log.warn("⚠️  [VULNERABLE] BƯỚC 5: LOGIN THÀNH CÔNG!");
        log.warn("⚠️  Email: {}, GoogleID: {}", email, googleId);
        log.warn("⚠️  Nếu đây là CSRF: Victim vừa đăng nhập bằng account của ATTACKER!");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

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

        model.addAttribute("email",       session.getAttribute("user_email"));
        model.addAttribute("name",        session.getAttribute("user_name"));
        model.addAttribute("picture",     session.getAttribute("user_picture"));
        model.addAttribute("googleId",    session.getAttribute("user_google_id"));
        model.addAttribute("serverType",  "VULNERABLE");
        model.addAttribute("port",        4002);
        model.addAttribute("loginVia",    session.getAttribute("login_via"));

        log.info("✅ [VULNERABLE] Profile page truy cập bởi: {}", session.getAttribute("user_email"));
        return "oauth2/profile";
    }

    /**
     * GET /oauth2/logout
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        log.info("🚪 [VULNERABLE] Đã đăng xuất");
        return "redirect:/oauth2/login";
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Xây dựng Google Authorization URL.
     * ⚠️ LỖ HỔNG: state = "" → không có gì để validate sau này.
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
               (state.isEmpty() ? "" : "&state=" + state); // Không gắn state!
    }

    /**
     * Bước 3: Đổi authorization_code lấy access_token từ Google.
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
            log.error("❌ [VULNERABLE] Lỗi khi đổi code lấy token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Bước 4: Lấy thông tin người dùng từ Google UserInfo endpoint.
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
            log.error("❌ [VULNERABLE] Lỗi khi lấy user info: {}", e.getMessage());
            return null;
        }
    }
}
