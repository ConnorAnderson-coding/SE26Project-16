package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seed/demo accounts use password {@code 123456}. Legacy BCrypt cost-10 hashes make
 * 100-way concurrent login miss the 3s SLA; upgrade matching hashes to the encoder's
 * current strength (8) once at startup.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class DemoPasswordUpgradeRunner implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "123456";
    /** Precomputed BCrypt(cost=8) of {@code 123456} — avoid re-encoding per user at startup. */
    private static final String DEMO_PASSWORD_HASH_COST_8 =
            "$2b$08$3F17LPgjlfYxsWPoFNVeOO5s6tK./7gtCInD/AXNqk6UYqQyYA7JK";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.upgrade-demo-password-hash:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<User> upgradedUsers = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            String hash = user.getPasswordHash();
            if (hash == null || hash.isBlank()) {
                continue;
            }
            // Already at cost ≤8 — skip expensive matches when possible.
            if (hash.contains("$08$") || hash.contains("$07$") || hash.contains("$06$")) {
                continue;
            }
            try {
                if (!passwordEncoder.matches(DEMO_PASSWORD, hash)) {
                    continue;
                }
            } catch (RuntimeException ex) {
                log.debug("skip password upgrade for {}: {}", user.getId(), ex.toString());
                continue;
            }
            user.setPasswordHash(DEMO_PASSWORD_HASH_COST_8);
            user.setUpdatedAt(LocalDateTime.now());
            upgradedUsers.add(user);
        }
        if (!upgradedUsers.isEmpty()) {
            userRepository.saveAll(upgradedUsers);
        }
        log.info("demo password hash upgrade finished: upgraded={}", upgradedUsers.size());
    }
}
