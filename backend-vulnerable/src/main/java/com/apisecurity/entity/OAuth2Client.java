package com.apisecurity.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "oauth2_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2Client {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "client_secret", nullable = false, length = 255)
    private String clientSecret;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Builder.Default
    @Column(name = "allowed_scopes", length = 255)
    private String allowedScopes = "read";
}