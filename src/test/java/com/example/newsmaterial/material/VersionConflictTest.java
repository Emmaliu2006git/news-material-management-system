package com.example.newsmaterial.material;

import com.example.newsmaterial.NewsMaterialApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class VersionConflictTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void staleExpectedCurrentVersionReturnsStableConflict() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = material(emma, projectId);

        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "version-two")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Second note\",\"textBody\":\"Second account.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.versionNumber").value(2));

        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "stale-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Stale note\",\"textBody\":\"Stale account.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    private String project(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"River flooding\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String material(MockHttpSession session, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/materials/text").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "conflict-material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"First note\",\"textBody\":\"First account.\"}".formatted(projectId)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
