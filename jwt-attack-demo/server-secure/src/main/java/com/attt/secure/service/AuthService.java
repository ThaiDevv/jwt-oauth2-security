package com.attt.secure.service;

import com.attt.secure.dto.RegisterRequest;
import com.attt.secure.model.User;
import com.attt.secure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * AuthService — Xử lý logic đăng ký, tìm user.
 * DataInitializer: tạo sẵn seed users khi server khởi động.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Tạo seed data khi server khởi động */
    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                User.builder().username("admin").password(passwordEncoder.encode("admin123")).role("admin").build(),
                User.builder().username("alice").password(passwordEncoder.encode("alice123")).role("user").build(),
                User.builder().username("bob").password(passwordEncoder.encode("bob123")).role("moderator").build()
            ));
            log.info("✅ [SECURE] Seed data: admin/admin123, alice/alice123, bob/bob123");
        }
    }

    /** Đăng ký user mới */
    public User register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new RuntimeException("Username '" + req.getUsername() + "' đã tồn tại");
        }
        User user = User.builder()
            .username(req.getUsername())
            .password(passwordEncoder.encode(req.getPassword()))
            .role(req.getRole() != null ? req.getRole() : "user")
            .build();
        return userRepository.save(user);
    }

    /** Tìm user theo username */
    public User findUser(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
}
