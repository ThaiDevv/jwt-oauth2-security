package com.attt.vulnerable.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@Slf4j
@Getter
@Component
public class RsaKeyConfig {
    private RSAPrivateKey privateKey;
    private RSAPublicKey  publicKey;
    private String        publicKeyPem;

    @PostConstruct
    public void generateKeyPair() throws Exception {
        log.warn("⚠️  [VULNERABLE] Generating RSA-2048 key pair...");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey  = (RSAPublicKey)  keyPair.getPublic();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        this.publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
        log.warn("⚠️  [VULNERABLE] RSA key pair generated — PUBLIC KEY EXPOSED at /api/public-key");
    }
}
