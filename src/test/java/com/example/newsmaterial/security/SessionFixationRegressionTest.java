package com.example.newsmaterial.security;

import com.example.newsmaterial.NewsMaterialApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class SessionFixationRegressionTest {
    private MockMvc mvc;
    private WebApplicationContext context;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        this.context = context;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginRotatesAnAnonymousSessionAndTheOldIdCannotAuthenticate() throws Exception {
        MockHttpSession anonymous = new MockHttpSession(context.getServletContext());
        MvcResult bootstrap = mvc.perform(get("/api/v1/auth/csrf").session(anonymous))
                .andExpect(status().isOk())
                .andReturn();
        anonymous = (MockHttpSession) bootstrap.getRequest().getSession(false);
        assertThat(anonymous).isNotNull();
        String oldSessionId = anonymous.getId();

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .session(anonymous)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(get("/api/v1/projects").cookie(new Cookie("JSESSIONID", oldSessionId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/projects").session(authenticated))
                .andExpect(status().isOk());
    }
}
