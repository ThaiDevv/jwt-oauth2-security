package com.attt.vulnerable.service;

import com.attt.vulnerable.model.User;
import com.attt.vulnerable.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                User.builder().username("admin").password(passwordEncoder.encode("admin123")).role("admin").build(),
                User.builder().username("alice").password(passwordEncoder.encode("alice123")).role("user").build(),
                User.builder().username("bob").password(passwordEncoder.encode("bob123")).role("moderator").build()
            ));
            log.info("✅ [VULNERABLE] Seed data: admin/admin123, alice/alice123, bob/bob123");
        }
    }

    public User register(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) throw new RuntimeException("Username đã tồn tại");
        return userRepository.save(User.builder()
            .username(username).password(passwordEncoder.encode(password))
            .role(role != null ? role : "user").build());
    }

    public User findUser(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
}
