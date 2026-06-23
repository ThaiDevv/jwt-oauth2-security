package com.apisecurity.repository;

import com.apisecurity.entity.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {
    AuthorizationCode findByCode(String code);
}
