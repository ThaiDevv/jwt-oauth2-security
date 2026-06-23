package com.apisecurity.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "blacklisted_tokens")
public class BlacklistedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "token_id", nullable = false, unique = true)
    private String tokenId;

    @Column(name = "expiry_time", nullable = false )
    private LocalDateTime expiryTime;

    @Column(name = "blacklisted_at", insertable = false, updatable = false)
    private LocalDateTime blacklistedAt;
}
