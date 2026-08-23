package com.example.newsmaterial.material;

import com.example.newsmaterial.NewsMaterialApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class VersionSummaryApiTest {
    private MockMvc mvc;
    private MockHttpSession emma;

    @BeforeEach
    void setUp(WebApplicationContext context) throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        emma = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void timelineReturnsOnlyLightweightVersionSummaries() throws Exception {
        MvcResult project = mvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Timeline\"}"))
                .andExpect(status().isCreated()).andReturn();
        String projectId = JsonPath.read(project.getResponse().getContentAsString(), "$.id");
        MvcResult v1 = mvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"First title\",\"textBody\":\"Large snapshot body\"}".formatted(projectId)))
                .andExpect(status().isCreated()).andReturn();
        String materialId = JsonPath.read(v1.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Second title\",\"textBody\":\"Another body\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/materials/{id}/versions", materialId).session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[0].createdBy").isNotEmpty())
                .andExpect(jsonPath("$[0].creatorUsername").value("emma"))
                .andExpect(jsonPath("$[0].versionCreatedTime").isNotEmpty())
                .andExpect(jsonPath("$[0].title").value("First title"))
                .andExpect(jsonPath("$[0].snapshot").doesNotExist())
                .andExpect(jsonPath("$[0].textBody").doesNotExist())
                .andExpect(jsonPath("$[0].files").doesNotExist());
    }
}
