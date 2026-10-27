package com.example.dms.config;

import com.example.dms.model.Role;
import com.example.dms.model.User;
import com.example.dms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds one admin and one normal user for local development. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.seed.admin-password}") private String adminPassword;
    @Value("${app.seed.user-password}") private String userPassword;

    @Override
    public void run(String... args) {
        seed("admin", "admin@dms.local", adminPassword, Role.ADMIN);
        seed("user", "user@dms.local", userPassword, Role.USER);
    }

    private void seed(String username, String email, String rawPassword, Role role) {
        if (users.existsByUsernameIgnoreCase(username)) return;
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(encoder.encode(rawPassword));
        u.setRole(role);
        users.save(u);
        log.warn("Seeded demo account '{}' ({}). Disable app.seed.enabled outside local dev.", username, role);
    }
}
