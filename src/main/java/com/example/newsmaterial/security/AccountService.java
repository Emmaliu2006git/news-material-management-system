package com.example.newsmaterial.security;

import com.example.newsmaterial.common.api.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {
    private static final int MIN_PASSWORD_CODE_POINTS = 8;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AccountService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public AppUser register(String username, String password) {
        validatePassword(password);
        if (users.findByUsername(username).isPresent()) {
            throw usernameTaken();
        }
        try {
            return users.saveAndFlush(new AppUser(
                    UUID.randomUUID(),
                    username,
                    passwords.encode(password),
                    Instant.now()));
        } catch (DataIntegrityViolationException exception) {
            throw usernameTaken();
        }
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        AppUser user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "Authentication required"));
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CURRENT_PASSWORD",
                    "Current password is incorrect");
        }
        user.changePasswordHash(passwords.encode(newPassword));
    }

    private void validatePassword(String password) {
        int codePoints = password.codePointCount(0, password.length());
        int encodedBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (codePoints < MIN_PASSWORD_CODE_POINTS || encodedBytes > MAX_BCRYPT_BYTES) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Password must be at least 8 characters and no more than 72 UTF-8 bytes");
        }
    }

    private ApiException usernameTaken() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "USERNAME_TAKEN",
                "That username is already in use");
    }
}
