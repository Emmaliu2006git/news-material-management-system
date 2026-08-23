package com.example.newsmaterial.analysis;

import com.example.newsmaterial.NewsMaterialApplication;
import com.example.newsmaterial.storage.StoragePort;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class AnalysisWorkerTest {
    @Autowired WebApplicationContext context;
    @Autowired ImageAnalysisRepository analyses;
    @Autowired StoragePort storage;
    @Autowired PlatformTransactionManager transactions;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void providerFailureExhaustsAfterThreeAttemptsWithoutHidingTheMaterial() throws Exception {
        Created created = createImageMaterial("worker-failure");
        ImageAnalysis analysis = analyses.findByFile_Id(created.fileId()).orElseThrow();
        AnalysisWorker worker = new AnalysisWorker(analyses, storage,
                (bytes, mimeType) -> { throw new IllegalStateException("provider unavailable"); },
                transactions, 3, Duration.ofSeconds(10));

        Instant attemptTime = analysis.getNextAttemptTime();
        assertThat(worker.processNext(attemptTime)).isTrue();
        attemptTime = analyses.findById(analysis.getId()).orElseThrow().getNextAttemptTime();
        assertThat(worker.processNext(attemptTime)).isTrue();
        attemptTime = analyses.findById(analysis.getId()).orElseThrow().getNextAttemptTime();
        assertThat(worker.processNext(attemptTime)).isTrue();

        ImageAnalysis failed = analyses.findById(analysis.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ImageAnalysisStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);

        MvcResult visible = mvc.perform(get("/api/v1/materials/{id}", created.materialId()).session(created.session()))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<String>read(visible.getResponse().getContentAsString(),
                "$.version.snapshot.content.files[0].imageAnalysis.status")).isEqualTo("FAILED");
    }

    @Test
    void successfulAnalysisStoresTheResultOnItsExactImage() throws Exception {
        Created created = createImageMaterial("worker-success");
        ImageAnalysis analysis = analyses.findByFile_Id(created.fileId()).orElseThrow();
        AnalysisWorker worker = new AnalysisWorker(analyses, storage,
                (bytes, mimeType) -> new ImageAnalysisResult("Flooded street", List.of("flood", "street")),
                transactions, 3, Duration.ofSeconds(10));

        assertThat(worker.processNext(analysis.getNextAttemptTime())).isTrue();

        MvcResult visible = mvc.perform(get("/api/v1/materials/{id}", created.materialId()).session(created.session()))
                .andExpect(status().isOk()).andReturn();
        String json = visible.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(json, "$.version.snapshot.content.files[0].imageAnalysis.status"))
                .isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(json, "$.version.snapshot.content.files[0].imageAnalysis.aiDescription"))
                .isEqualTo("Flooded street");
        assertThat(JsonPath.<List<String>>read(json, "$.version.snapshot.content.files[0].imageAnalysis.aiTags"))
                .containsExactly("flood", "street");
    }

    private Created createImageMaterial(String key) throws Exception {
        MockHttpSession session = login("emma");
        MvcResult project = mvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Worker project\"}"))
                .andExpect(status().isCreated()).andReturn();
        String projectId = JsonPath.read(project.getResponse().getContentAsString(), "$.id");
        byte[] bytes = ("image-" + key).getBytes();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"flood.jpg\",\"mimeType\":\"image/jpeg\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(bytes.length, sha)))
                .andExpect(status().isCreated()).andReturn();
        String intentId = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].intentId");
        String uploadUrl = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(uploadUrl).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("image/jpeg").content(bytes))
                .andExpect(status().isNoContent());
        MvcResult material = mvc.perform(post("/api/v1/materials/file").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Flood image\",\"uploadIntentIds\":[\"%s\"]}"
                                .formatted(projectId, intentId)))
                .andExpect(status().isCreated()).andReturn();
        String json = material.getResponse().getContentAsString();
        return new Created(session, UUID.fromString(JsonPath.read(json, "$.id")),
                UUID.fromString(JsonPath.read(json, "$.version.snapshot.content.files[0].id")));
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private record Created(MockHttpSession session, UUID materialId, UUID fileId) {
    }
}
