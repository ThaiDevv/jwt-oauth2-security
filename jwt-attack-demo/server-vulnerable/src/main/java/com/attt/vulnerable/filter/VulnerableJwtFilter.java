package com.attt.vulnerable.filter;

import com.attt.vulnerable.config.RsaKeyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class VulnerableJwtFilter extends OncePerRequestFilter {

    private final RsaKeyConfig rsaKeyConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = vulnerableLibraryVerify(token,
                    rsaKeyConfig.getPublicKeyPem().getBytes(StandardCharsets.UTF_8));

            log.info("[VULNERABLE] Token verified: sub={} role={}",
                    claims.getSubject(), claims.get("role"));

            String role = claims.get("role", String.class);
            if (role == null)
                role = "user";

            var auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            auth.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            log.warn("[VULNERABLE] Token rejected: {}", e.getMessage());
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "Invalid token: " + e.getMessage(),
                    "serverType", "vulnerable")));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * MÔ PHỎNG hành vi bên trong thư viện JWT bị lỗi.
     *
     * Đây là cách các thư viện JWT cũ thực sự hoạt động bên trong:
     * Hàm nhận token + key material (byte[]), rồi TỰ ĐỘNG đọc trường
     * "alg" từ JWT header để quyết định dùng thuật toán nào verify.
     *
     * Lỗ hổng: Attacker kiểm soát trường "alg" trong JWT header
     * → Attacker đổi alg từ RS256 sang HS256
     * → Thư viện dùng publicKey bytes làm HMAC secret
     * → Attacker cũng biết publicKey → ký được token hợp lệ!
     *
     * @param token       JWT token string
     * @param keyMaterial Public key bytes (do developer truyền vào)
     * @return Claims đã verify
     */
    private Claims vulnerableLibraryVerify(String token, byte[] keyMaterial) throws Exception {
        // THƯ VIỆN đọc JWT header để xác định algorithm
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new JwtException("Invalid JWT format");
        }

        String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> jwtHeader = objectMapper.readValue(headerJson, Map.class);
        String alg = (String) jwtHeader.get("alg");

        log.info("[JWT-LIB] Auto-detected algorithm from token header: {}", alg);

        switch (alg) {
            case "HS256":
            case "HS384":
            case "HS512":
                // Thư viện thấy HMAC algorithm → dùng keyMaterial làm HMAC secret
                // Vấn đề: keyMaterial ở đây chính là PUBLIC KEY bytes!
                log.warn("[JWT-LIB] HMAC algorithm detected → using provided key as HMAC secret");
                SecretKey hmacKey = Keys.hmacShaKeyFor(keyMaterial);
                return Jwts.parserBuilder()
                        .setSigningKey(hmacKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

            case "none":
                // Thư viện cũ chấp nhận alg=none → không verify chữ ký
                log.warn("[JWT-LIB] alg=none → skipping signature verification");
                String payloadJson = new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
                return buildClaimsFromMap(payload);

            default:
                // RS256, RS384, RS512... → verify bằng RSA public key (bình thường)
                log.info("[JWT-LIB] RSA algorithm → standard RSA verification");
                return Jwts.parserBuilder()
                        .setSigningKey(rsaKeyConfig.getPublicKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
        }
    }

    private Claims buildClaimsFromMap(Map<String, Object> payload) {
        Claims claims = Jwts.claims();
        claims.setSubject((String) payload.getOrDefault("sub", "unknown"));
        claims.put("role", payload.getOrDefault("role", "user"));
        claims.put("userId", payload.get("userId"));
        return claims;
    }
}
