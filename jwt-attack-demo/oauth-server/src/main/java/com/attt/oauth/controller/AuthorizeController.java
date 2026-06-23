package com.attt.oauth.controller;

import com.attt.oauth.service.AuthCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

/**
 * AuthorizeController — Xử lý OAuth2 Authorization Code Flow.
 *
 * LỖ HỔNG: Không validate redirect_uri whitelist.
 * Secure OAuth2 server phải kiểm tra redirect_uri có trong danh sách đã đăng ký.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthorizeController {

    private final AuthCodeService authCodeService;

    /**
     * GET /authorize — Hiển thị trang consent
     * LỖ HỔNG: Không kiểm tra redirect_uri có trong whitelist
     */
    @GetMapping("/authorize")
    public String authorize(
        @RequestParam String client_id,
        @RequestParam String redirect_uri,
        @RequestParam(defaultValue = "openid profile email") String scope,
        @RequestParam(required = false, defaultValue = "") String state,
        Model model
    ) {
        log.warn("⚠️  [OAUTH] GET /authorize | client_id={} | redirect_uri={} — KHÔNG VALIDATE redirect_uri!", client_id, redirect_uri);
        // LỖ HỔNG: Không có kiểm tra:
        //   if (!ALLOWED_REDIRECT_URIS.contains(redirect_uri)) { return error; }

        model.addAttribute("clientId",    client_id);
        model.addAttribute("redirectUri", redirect_uri);
        model.addAttribute("scope",       scope);
        model.addAttribute("state",       state);
        return "consent"; // Thymeleaf template
    }

    /**
     * POST /authorize — User click Allow
     * LỖ HỔNG: Redirect về redirect_uri không được kiểm tra — kể cả URL của attacker
     */
    @PostMapping("/authorize")
    public RedirectView doAuthorize(
        @RequestParam String redirect_uri,
        @RequestParam(required = false, defaultValue = "") String state,
        @RequestParam String client_id
    ) {
        String code = authCodeService.generateCode(client_id);
        log.error("🚨 [OAUTH] Auth code {} issued | Redirecting to: {} — ATTACKER URL?", code, redirect_uri);
        // LỖ HỔNG: Redirect về redirect_uri của ATTACKER mà không kiểm tra
        String location = redirect_uri + "?code=" + code + (state.isEmpty() ? "" : "&state=" + state);
        return new RedirectView(location);
    }
}
