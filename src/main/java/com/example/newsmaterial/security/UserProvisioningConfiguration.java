package com.example.newsmaterial.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.io.Console;
import java.util.Arrays;

@Configuration
@Profile("provision-user")
class UserProvisioningConfiguration {
    private static final String USERNAME = "NEWS_MATERIAL_PROVISION_USERNAME";
    private static final String PASSWORD = "NEWS_MATERIAL_PROVISION_PASSWORD";

    @Bean
    ApplicationRunner provisionUser(UserProvisioningService provisioning, Environment environment,
                                    ConfigurableApplicationContext context) {
        return args -> {
            char[] password = null;
            try {
                String username = environment.getProperty(USERNAME);
                if (username == null || username.isBlank()) {
                    throw new IllegalArgumentException(USERNAME + " is required");
                }
                password = password(environment);
                UserProvisioningService.Result result = provisioning.provision(username, password);
                System.out.printf("Account %s: %s%n", result.username(), result.outcome());
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
                context.close();
            }
        };
    }

    private char[] password(Environment environment) {
        String configured = environment.getProperty(PASSWORD);
        if (configured != null && !configured.isBlank()) {
            return configured.toCharArray();
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalArgumentException(
                    PASSWORD + " is required when no interactive console is available");
        }
        char[] entered = console.readPassword("Password: ");
        if (entered == null || entered.length == 0) {
            throw new IllegalArgumentException("Password is required");
        }
        return entered;
    }
}
