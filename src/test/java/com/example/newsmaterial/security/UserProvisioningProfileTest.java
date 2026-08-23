package com.example.newsmaterial.security;

import com.example.newsmaterial.NewsMaterialApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class UserProvisioningProfileTest {
    @Test
    void provisioningOnlyProfileStartsWithoutUnrelatedExternalAdapters() {
        String username = "profile-" + UUID.randomUUID();

        assertThatCode(() -> {
            SpringApplication application = new SpringApplication(NewsMaterialApplication.class);
            application.setAdditionalProfiles("provision-user");
            ConfigurableApplicationContext context = application.run(
                    "--spring.main.web-application-type=none",
                    "--spring.datasource.url=jdbc:h2:mem:provision_profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.datasource.username=sa",
                    "--spring.datasource.password=",
                    "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                    "--spring.jpa.hibernate.ddl-auto=create-drop",
                    "--spring.flyway.enabled=false",
                    "--NEWS_MATERIAL_PROVISION_USERNAME=" + username,
                    "--NEWS_MATERIAL_PROVISION_PASSWORD=a-long-profile-password");
            if (context.isActive()) {
                context.close();
            }
        }).doesNotThrowAnyException();
    }
}
