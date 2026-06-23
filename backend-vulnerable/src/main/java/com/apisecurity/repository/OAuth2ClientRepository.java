package com.apisecurity.repository;

import com.apisecurity.entity.OAuth2Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuth2ClientRepository extends JpaRepository<OAuth2Client, String> {
    OAuth2Client findByClientId(String clientId);
}
