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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class TextMaterialLifecycleTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void versionOneAndHistoricalVersionUseTheSameSnapshotLayout() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = create(emma, projectId, "V1 title", "V1 body", "first-create");

        mockMvc.perform(get("/api/v1/materials/{id}", materialId).session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.materialCreatedTime").exists())
                .andExpect(jsonPath("$.version.versionNumber").value(1))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectTitle").value("Tidal flooding"))
                .andExpect(jsonPath("$.ownerId").isNotEmpty())
                .andExpect(jsonPath("$.ownerUsername").value("emma"))
                .andExpect(jsonPath("$.currentVersionId").isNotEmpty())
                .andExpect(jsonPath("$.version.id").isNotEmpty())
                .andExpect(jsonPath("$.version.createdBy").isNotEmpty())
                .andExpect(jsonPath("$.version.creatorUsername").value("emma"))
                .andExpect(jsonPath("$.version.versionCreatedTime").exists())
                .andExpect(jsonPath("$.version.snapshot.title").value("V1 title"))
                .andExpect(jsonPath("$.version.snapshot.eventTime").value("2026-08-01T10:15:30Z"))
                .andExpect(jsonPath("$.version.snapshot.location").value("North district"))
                .andExpect(jsonPath("$.version.snapshot.manualTags[0]").value("flood"))
                .andExpect(jsonPath("$.version.snapshot.content.kind").value("TEXT"))
                .andExpect(jsonPath("$.version.snapshot.content.textBody").value("V1 body"))
                .andExpect(jsonPath("$.historical").value(false));

        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "version-two-layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"V2 title\",\"textBody\":\"V2 body\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/materials/{id}/versions/{number}", materialId, 1).session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historical").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.version.versionNumber").value(1))
                .andExpect(jsonPath("$.version.snapshot.title").value("V1 title"))
                .andExpect(jsonPath("$.version.snapshot.content.kind").value("TEXT"))
                .andExpect(jsonPath("$.version.snapshot.content.textBody").value("V1 body"));

        mockMvc.perform(get("/api/v1/materials/{id}/versions", materialId).session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("\u0024[0].versionNumber").value(1))
                .andExpect(jsonPath("\u0024[1].versionNumber").value(2));
    }

    @Test
    void currentAndClickedHistoricalViewsHaveTheSameMaterialViewAndSnapshotShape() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = create(emma, projectId, "First version", "First body", "shape-first");
        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "shape-second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Second version\",\"textBody\":\"Second body\"}"))
                .andExpect(status().isCreated());

        MvcResult current = mockMvc.perform(get("/api/v1/materials/{id}", materialId).session(emma))
                .andExpect(status().isOk()).andReturn();
        MvcResult historical = mockMvc.perform(get("/api/v1/materials/{id}/versions/{number}", materialId, 1).session(emma))
                .andExpect(status().isOk()).andReturn();

        java.util.Map<String, Object> currentView = JsonPath.read(current.getResponse().getContentAsString(), "$");
        java.util.Map<String, Object> historicalView = JsonPath.read(historical.getResponse().getContentAsString(), "$");
        assertThat(currentView.keySet()).isEqualTo(historicalView.keySet());
        assertThat(((java.util.Map<String, Object>) currentView.get("version")).keySet())
                .isEqualTo(((java.util.Map<String, Object>) historicalView.get("version")).keySet());
        assertThat(((java.util.Map<String, Object>) ((java.util.Map<String, Object>) currentView.get("version")).get("snapshot")).keySet())
                .isEqualTo(((java.util.Map<String, Object>) ((java.util.Map<String, Object>) historicalView.get("version")).get("snapshot")).keySet());
        assertThat(((java.util.Map<String, Object>) ((java.util.Map<String, Object>) ((java.util.Map<String, Object>) currentView.get("version")).get("snapshot")).get("content")).keySet())
                .isEqualTo(((java.util.Map<String, Object>) ((java.util.Map<String, Object>) ((java.util.Map<String, Object>) historicalView.get("version")).get("snapshot")).get("content")).keySet());
        assertThat(currentView.get("historical")).isEqualTo(false);
        assertThat(currentView.get("canEdit")).isEqualTo(true);
        assertThat(historicalView.get("historical")).isEqualTo(true);
        assertThat(historicalView.get("canEdit")).isEqualTo(false);
    }

    @Test
    void explicitVersionRouteIsReadOnlyEvenWhenItSelectsTheCurrentVersion() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = create(emma, projectId, "Current v1", "Current body", "current-route-create");

        mockMvc.perform(get("/api/v1/materials/{id}/versions/{number}", materialId, 1).session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historical").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.version.versionNumber").value(1))
                .andExpect(jsonPath("$.version.snapshot.title").value("Current v1"));
    }

    @Test
    void createIdempotencyUsesNormalizedPersistedFieldsAndKey() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String firstRequest = "{\"projectId\":\"" + projectId + "\",\"title\":\"  Normalized title  \"," +
                "\"location\":\"  North district  \",\"manualTags\":[\" flood \",\" urgent \"]," +
                "\"textBody\":\"  Field report  \"}";

        MvcResult first = mockMvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "  normalized-create  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.snapshot.title").value("Normalized title"))
                .andExpect(jsonPath("$.version.snapshot.location").value("North district"))
                .andExpect(jsonPath("$.version.snapshot.manualTags[0]").value("flood"))
                .andExpect(jsonPath("$.version.snapshot.manualTags[1]").value("urgent"))
                .andExpect(jsonPath("$.version.snapshot.content.textBody").value("Field report"))
                .andReturn();

        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "normalized-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"title\":\"Normalized title\"," +
                                "\"location\":\"North district\",\"manualTags\":[\"flood\",\"urgent\"]," +
                                "\"textBody\":\"Field report\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void versionIdempotencyUsesNormalizedPersistedFieldsAndKey() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = create(emma, projectId, "V1", "V1 body", "normalized-version-create");

        MvcResult first = mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "  normalized-version  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"  Verified note  \"," +
                                "\"location\":\"  River Street  \",\"manualTags\":[\" verified \"]," +
                                "\"textBody\":\"  Confirmed body  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.snapshot.title").value("Verified note"))
                .andExpect(jsonPath("$.version.snapshot.location").value("River Street"))
                .andExpect(jsonPath("$.version.snapshot.manualTags[0]").value("verified"))
                .andExpect(jsonPath("$.version.snapshot.content.textBody").value("Confirmed body"))
                .andReturn();

        String versionId = JsonPath.read(first.getResponse().getContentAsString(), "$.version.id");
        mockMvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "normalized-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Verified note\"," +
                                "\"location\":\"River Street\",\"manualTags\":[\"verified\"]," +
                                "\"textBody\":\"Confirmed body\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.id").value(versionId));
    }

    @Test
    void repeatedIdempotencyKeyReplaysOriginalMaterialAndRejectsDifferentPayload() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        String materialId = create(emma, projectId, "Idempotent title", "Idempotent body", "same-create");

        MvcResult replay = mockMvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "same-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectId, "Idempotent title", "Idempotent body")))
                .andExpect(status().isCreated()).andReturn();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(replay.getResponse().getContentAsString(), "$.id")).isEqualTo(materialId);

        mockMvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "same-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(projectId, "Changed title", "Changed body")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private String project(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Tidal flooding\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String create(MockHttpSession session, String projectId, String title, String body, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/materials/text").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload(projectId, title, body)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String payload(String projectId, String title, String body) {
        return "{\"projectId\":\"%s\",\"title\":\"%s\",\"eventTime\":\"2026-08-01T10:15:30Z\",\"location\":\"North district\",\"manualTags\":[\"flood\"],\"textBody\":\"%s\"}".formatted(projectId, title, body);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
