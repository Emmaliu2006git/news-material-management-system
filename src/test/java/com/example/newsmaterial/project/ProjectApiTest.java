package com.example.newsmaterial.project;

import com.example.newsmaterial.NewsMaterialApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class ProjectApiTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void authenticatedUserCanListAndCreateProject() throws Exception {
        MockHttpSession emma = login("emma");
        mockMvc.perform(get("/api/v1/projects").session(emma)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"City Flood\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("City Flood"))
                .andExpect(jsonPath("$.creatorUsername").value("emma"));
    }

    @Test
    void creatorCanRenameButAnotherAuthenticatedUserCannot() throws Exception {
        MockHttpSession emma = login("emma");
        MvcResult created = mockMvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Flood Response\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(patch("/api/v1/projects/{id}", id).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Flood Response Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Flood Response Updated"));
        MockHttpSession alex = login("alex");
        mockMvc.perform(patch("/api/v1/projects/{id}", id).session(alex)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Not Allowed\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validationFailureUsesTheStableErrorEnvelope() throws Exception {
        MockHttpSession emma = login("emma");
        mockMvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.details").isArray());
    }
    @Test
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfEndpointProvidesTokenAndCookieForMutation() throws Exception {
        MockHttpSession emma = login("emma");
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf").session(emma)).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        jakarta.servlet.http.Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/v1/projects").session(emma).cookie(cookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"CSRF Bootstrap\"}"))
                .andExpect(status().isCreated());
    }
    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
