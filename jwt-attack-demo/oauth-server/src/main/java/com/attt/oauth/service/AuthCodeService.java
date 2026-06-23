package com.attt.oauth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthCodeService — Quản lý authorization codes và access tokens.
 * In-memory storage (phục vụ demo).
 */
@Slf4j
@Service
public class AuthCodeService {

    private final Map<String, String>              codes  = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> tokens = new ConcurrentHashMap<>();

    /** Tạo authorization code ngẫu nhiên */
    public String generateCode(String clientId) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        codes.put(code, clientId);
        log.warn("🎟️  [OAUTH] Auth code generated: {} | clientId: {}", code, clientId);
        return code;
    }

    /** Đổi auth code lấy access token */
    public String exchangeCode(String code) {
        if (!codes.containsKey(code)) {
            log.warn("[OAUTH] Invalid or expired code: {}", code);
            return null;
        }
        String clientId = codes.remove(code);
        String accessToken = "at_" + UUID.randomUUID().toString().replace("-", "");
        tokens.put(accessToken, Map.of(
            "sub",      "user_" + clientId,
            "name",     "Demo User",
            "email",    "demo@example.com",
            "clientId", clientId,
            "iat",      System.currentTimeMillis() / 1000
        ));
        log.info("✅ [OAUTH] Code exchanged for access token: {}", accessToken.substring(0, 10) + "...");
        return accessToken;
    }

    /** Lấy thông tin user từ access token */
    public Map<String, Object> getUserInfo(String token) {
        return tokens.get(token);
    }
}
