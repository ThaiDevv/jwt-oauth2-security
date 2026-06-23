package com.attt.secure.filter;

import com.attt.secure.config.RsaKeyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ✅ JWT Filter AN TOÀN — Chỉ chấp nhận RS256
 *
 * Cơ chế bảo vệ chống CVE-2015-9235:
 *  1. Decode JWT header thủ công TRƯỚC khi verify
 *  2. Từ chối ngay nếu algorithm != RS256
 *  3. Verify chữ ký bằng RSA public key với algorithm whitelist
 */
@Slf4j
@RequiredArgsConstructor
public class SecureJwtFilter extends OncePerRequestFilter {

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
            // ============================================================
            // BƯỚC 1: Decode JWT header thủ công — TRƯỚC KHI verify
            // Không để JJWT tự parse vì nó sẽ dùng alg trong header
            // ============================================================
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                writeError(response, 401, "Invalid JWT format", null);
                return;
            }

            String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> jwtHeader = objectMapper.readValue(headerJson, Map.class);
            String alg = (String) jwtHeader.get("alg");

            log.info("🔍 [SECURE] JWT header alg = {}", alg);

            // ============================================================
            // BƯỚC 2: Whitelist algorithm — CHỈ RS256 được phép
            // Đây là điểm khác biệt cốt lõi so với VulnerableJwtFilter
            // Nếu attacker gửi alg=HS256 → bị từ chối ngay tại đây
            // ============================================================
            if (!"RS256".equals(alg)) {
                log.warn("🚫 [SECURE] Algorithm '{}' BỊ TỪ CHỐI — chỉ chấp nhận RS256!", alg);
                writeError(response, 401,
                    "Algorithm not allowed",
                    "Received: " + alg + " | Expected: RS256 only | Protected against CVE-2015-9235"
                );
                return;
            }

            // ============================================================
            // BƯỚC 3: Verify chữ ký với RSA Public Key
            // JJWT sẽ xác nhận chữ ký RSA-2048
            // ============================================================
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(rsaKeyConfig.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

            log.info("✅ [SECURE] Token hợp lệ | sub={} | role={}", claims.getSubject(), claims.get("role"));

            // Set SecurityContext cho Spring Security
            String role = claims.get("role", String.class);
            if (role == null) role = "user";
            var auth = new UsernamePasswordAuthenticationToken(
                claims.getSubject(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            );
            auth.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (ExpiredJwtException e) {
            writeError(response, 401, "Token expired", e.getMessage());
            return;
        } catch (JwtException e) {
            writeError(response, 401, "Invalid token", e.getMessage());
            return;
        } catch (Exception e) {
            writeError(response, 500, "Server error", e.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status,
                            String error, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        if (detail != null) body.put("detail", detail);
        body.put("serverType", "secure");
        body.put("timestamp", System.currentTimeMillis());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
