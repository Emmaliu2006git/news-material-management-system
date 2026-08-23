package com.example.newsmaterial.web;

import com.example.newsmaterial.NewsMaterialApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class SpaPackagingTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void frontendIndexIsPackagedOnTheApplicationClasspath() {
        assertThat(new ClassPathResource("static/index.html").exists()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/login",
            "/projects",
            "/projects/123",
            "/projects/123/new",
            "/materials/456",
            "/materials/456/edit",
            "/materials/456/versions/2"
    })
    void knownClientRoutesForwardToThePackagedApplication(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/does-not-exist", "/actuator/health", "/assets/missing.js"})
    void nonClientRoutesAreNeverForwarded(String route) throws Exception {
        var result = mockMvc.perform(get(route)).andReturn();

        assertThat(result.getResponse().getForwardedUrl()).isNull();
    }
}
