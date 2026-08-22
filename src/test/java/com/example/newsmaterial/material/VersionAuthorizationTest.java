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
class VersionAuthorizationTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlyTheMaterialOwnerCanCreateTheNextVersion() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma, "Flood response");
        String materialId = material(emma, projectId, "Initial note", "Initial account.");

        MockHttpSession alex = login("alex");
        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(alex)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "unauthorized-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Edited note\",\"textBody\":\"Edited account.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String project(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String material(MockHttpSession session, String projectId, String title, String textBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/materials/text").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "owner-material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"%s\",\"textBody\":\"%s\"}".formatted(projectId, title, textBody)))
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
