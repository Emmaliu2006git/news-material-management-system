package com.example.newsmaterial.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserProvisioningService {
    public enum Outcome {
        CREATED,
        ALREADY_EXISTS
    }

    public record Result(UUID userId, String username, Outcome outcome) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserProvisioningService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public Result provision(String rawUsername, char[] password) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isEmpty() || username.length() > 64) {
            throw new IllegalArgumentException("Username must be nonblank and at most 64 characters");
        }
        if (password == null || password.length < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }

        return users.findByUsername(username)
                .map(existing -> new Result(existing.getId(), existing.getUsername(), Outcome.ALREADY_EXISTS))
                .orElseGet(() -> {
                    AppUser created = users.saveAndFlush(new AppUser(
                            UUID.randomUUID(), username, passwords.encode(new String(password)), Instant.now()));
                    return new Result(created.getId(), created.getUsername(), Outcome.CREATED);
                });
    }
}
