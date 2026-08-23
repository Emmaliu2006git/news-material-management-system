package com.example.newsmaterial.security;

import com.example.newsmaterial.NewsMaterialApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class UserProvisioningServiceTest {
    @Autowired
    private UserProvisioningService provisioning;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void createsABcryptAccountIdempotentlyWithoutChangingAnExistingPassword() {
        String username = "cloud-" + UUID.randomUUID();
        char[] password = "a-long-cloud-password".toCharArray();

        UserProvisioningService.Result first = provisioning.provision(username, password);
        UserProvisioningService.Result replay = provisioning.provision(username, "different-password".toCharArray());

        assertThat(first.outcome()).isEqualTo(UserProvisioningService.Outcome.CREATED);
        assertThat(replay.outcome()).isEqualTo(UserProvisioningService.Outcome.ALREADY_EXISTS);
        assertThat(replay.userId()).isEqualTo(first.userId());
        AppUser stored = users.findByUsername(username).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(passwords.matches(new String(password), stored.getPasswordHash())).isTrue();
        assertThat(passwords.matches("different-password", stored.getPasswordHash())).isFalse();
        assertThat(users.findAll().stream().filter(user -> user.getUsername().equals(username))).hasSize(1);
    }
}
