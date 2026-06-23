package com.apisecurity.repository;

import com.apisecurity.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    BlacklistedToken findByToken(String token);
    Boolean existsByTokenId(String tokenId);
}
