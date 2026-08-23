package com.example.newsmaterial.search;

import com.example.newsmaterial.NewsMaterialApplication;
import com.example.newsmaterial.analysis.AnalysisWorker;
import com.example.newsmaterial.analysis.ImageAnalysisStatus;
import com.example.newsmaterial.analysis.ImageAnalysis;
import com.example.newsmaterial.analysis.ImageAnalysisRepository;
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

import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class SearchApiTest {
    @Autowired WebApplicationContext context;
    @Autowired AnalysisWorker worker;
    @Autowired ImageAnalysisRepository analyses;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void searchesOnlyCurrentVersionsComposesFiltersAndReturnsOneRowPerMaterial() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma, "Current search");
        String first = createText(emma, projectId, "Old flood headline", "obsolete-signal",
                "2026-08-20T10:00:00Z", "Downtown", List.of("urgent", "flood"), "search-first");
        String ownerId = JsonPath.read(first, "$.ownerId");
        String firstMaterialId = JsonPath.read(first, "$.id");

        mvc.perform(post("/api/v1/materials/{id}/versions", firstMaterialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "search-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Current river dispatch\",\"eventTime\":\"2026-08-20T10:00:00Z\",\"location\":\"Downtown\",\"manualTags\":[\"urgent\",\"flood\"],\"textBody\":\"current-signal\"}"))
                .andExpect(status().isCreated());

        createText(emma, projectId, "Second current dispatch", "current-signal",
                null, null, List.of(), "search-second");

        MvcResult obsolete = mvc.perform(get("/api/v1/projects/{id}/materials", projectId)
                        .session(emma).param("q", "obsolete-signal"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<Object>>read(obsolete.getResponse().getContentAsString(), "$")).isEmpty();

        MvcResult current = mvc.perform(get("/api/v1/projects/{id}/materials", projectId)
                        .session(emma).param("q", "CURRENT-SIGNAL"))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = JsonPath.read(current.getResponse().getContentAsString(), "$[*].id");
        assertThat(ids).hasSize(2);
        assertThat(ids.stream().collect(Collectors.toSet())).hasSize(2);
        assertThat(JsonPath.<List<Integer>>read(current.getResponse().getContentAsString(), "$[*].currentVersionNumber"))
                .contains(2, 1);

        MvcResult filtered = mvc.perform(get("/api/v1/projects/{id}/materials", projectId)
                        .session(emma)
                        .param("q", "river")
                        .param("materialType", "TEXT")
                        .param("ownerId", ownerId)
                        .param("createdFrom", "2000-01-01T00:00:00Z")
                        .param("createdTo", "2100-01-01T00:00:00Z")
                        .param("eventFrom", "2026-08-20T00:00:00Z")
                        .param("eventTo", "2026-08-21T00:00:00Z")
                        .param("location", "down")
                        .param("tag", "URGENT"))
                .andExpect(status().isOk()).andReturn();
        List<String> filteredIds = JsonPath.read(filtered.getResponse().getContentAsString(), "$[*].id");
        assertThat(filteredIds).containsExactly(firstMaterialId);
    }

    @Test
    void aiSearchReturnsTheExactMatchedImageIdWithoutDuplicatingTheMaterial() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma, "AI search");
        Upload image = upload(emma, "street.jpg", "image/jpeg", "street-image".getBytes());
        Upload note = upload(emma, "notes.txt", "text/plain", "field notes".getBytes());
        MvcResult created = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "ai-search-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Evidence files\",\"uploadIntentIds\":[\"%s\",\"%s\"]}"
                                .formatted(projectId, image.id(), note.id())))
                .andExpect(status().isCreated()).andReturn();
        String json = created.getResponse().getContentAsString();
        String materialId = JsonPath.read(json, "$.id");
        UUID imageFileId = UUID.fromString(JsonPath.read(json, "$.version.snapshot.content.files[0].id"));
        UUID noteFileId = UUID.fromString(JsonPath.read(json, "$.version.snapshot.content.files[1].id"));
        ImageAnalysis analysis = analyses.findByFile_Id(imageFileId).orElseThrow();

        Instant runAt = Instant.now().plusSeconds(3600);
        int maxJobs = Math.toIntExact(analyses.count()) + 1;
        for (int processed = 0;
             processed < maxJobs && analyses.findById(analysis.getId()).orElseThrow().getStatus() != ImageAnalysisStatus.COMPLETED;
             processed++) {
            assertThat(worker.processNext(runAt)).isTrue();
        }
        assertThat(analyses.findById(analysis.getId()).orElseThrow().getStatus()).isEqualTo(ImageAnalysisStatus.COMPLETED);

        MvcResult result = mvc.perform(get("/api/v1/projects/{id}/materials", projectId)
                        .session(emma).param("q", "demo-analysis"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<String>>read(result.getResponse().getContentAsString(), "$[*].id"))
                .containsExactly(materialId);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$[0].matchedFileId"))
                .isEqualTo(imageFileId.toString());

        mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "ai-search-file-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Evidence notes only\",\"files\":[" +
                                "{\"kind\":\"EXISTING\",\"fileId\":\"" + noteFileId + "\"}]}"))
                .andExpect(status().isCreated());

        MvcResult historicalAi = mvc.perform(get("/api/v1/projects/{id}/materials", projectId)
                        .session(emma).param("q", "demo-analysis"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<Object>>read(
                historicalAi.getResponse().getContentAsString(), "$")).isEmpty();
    }

    private String createText(MockHttpSession session, String projectId, String title, String text,
                              String eventTime, String location, List<String> tags, String key) throws Exception {
        String event = eventTime == null ? "null" : "\"" + eventTime + "\"";
        String place = location == null ? "null" : "\"" + location + "\"";
        String tagJson = tags.stream().map(tag -> "\"" + tag + "\"").collect(Collectors.joining(","));
        MvcResult result = mvc.perform(post("/api/v1/materials/text").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"%s\",\"eventTime\":%s,\"location\":%s,\"manualTags\":[%s],\"textBody\":\"%s\"}"
                                .formatted(projectId, title, event, place, tagJson, text)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getContentAsString();
    }

    private String project(MockHttpSession session, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private Upload upload(MockHttpSession session, String name, String mime, byte[] bytes) throws Exception {
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"%s\",\"mimeType\":\"%s\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(name, mime, bytes.length, checksum)))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(mime).content(bytes))
                .andExpect(status().isNoContent());
        return new Upload(id);
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private record Upload(String id) {
    }
}
