package com.example.newsmaterial.security;

import com.example.newsmaterial.NewsMaterialApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = NewsMaterialApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cookie_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "app.analysis.provider=local",
                "app.analysis.scheduling-enabled=false"
        })
class SessionCookieSecurityTest {
    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @BeforeEach
    void createAccount() {
        if (users.findByUsername("cookie-user").isEmpty()) {
            users.save(new AppUser(UUID.randomUUID(), "cookie-user",
                    passwords.encode("cookie-test-password"), Instant.now()));
        }
    }

    @Test
    void productionDefaultSessionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"cookie-user\",\"password\":\"cookie-test-password\"}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow();
        String normalized = sessionCookie.toLowerCase(Locale.ROOT);
        assertThat(normalized).contains("secure");
        assertThat(normalized).contains("httponly");
        assertThat(normalized).contains("samesite=lax");
    }
}
