package com.example.newsmaterial.analysis;

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

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class ImageAnalysisApiTest {
    @Autowired WebApplicationContext context;
    @Autowired ImageAnalysisRepository analyses;
    @Autowired ImageAnalysisService analysisService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsOneVisiblePendingAnalysisPerImageAndNoneForNonImages() throws Exception {
        MockHttpSession emma = login("emma");
        long analysisCountBefore = analyses.count();
        String projectId = project(emma);
        Upload image = upload(emma, "flood.jpg", "image/jpeg", "image".getBytes());
        Upload note = upload(emma, "notes.txt", "text/plain", "notes".getBytes());

        MvcResult created = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "analysis-association")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Flood evidence\",\"uploadIntentIds\":[\"%s\",\"%s\"]}"
                                .formatted(projectId, image.id(), note.id())))
                .andExpect(status().isCreated())
                .andReturn();

        String json = created.getResponse().getContentAsString();
        String imageFileId = JsonPath.read(json, "$.version.snapshot.content.files[0].id");
        assertThat(JsonPath.<String>read(json, "$.version.snapshot.content.files[0].imageAnalysis.status"))
                .isEqualTo("PENDING");
        assertThat(JsonPath.<Object>read(json, "$.version.snapshot.content.files[0].imageAnalysis.aiDescription"))
                .isNull();
        assertThat(JsonPath.<Object>read(json, "$.version.snapshot.content.files[1].imageAnalysis"))
                .isNull();
        assertThat(analyses.countByFile_Id(java.util.UUID.fromString(imageFileId))).isEqualTo(1);
        assertThat(analyses.count()).isEqualTo(analysisCountBefore + 1);

        UUID imageId = UUID.fromString(imageFileId);
        UUID noteId = UUID.fromString(JsonPath.read(json, "$.version.snapshot.content.files[1].id"));
        analyses.delete(analyses.findByFile_Id(imageId).orElseThrow());
        analyses.flush();
        assertThat(analyses.countByFile_Id(imageId)).isZero();

        analysisService.reconcileMissing(Instant.parse("2026-08-22T12:00:00Z"));
        analysisService.reconcileMissing(Instant.parse("2026-08-22T12:01:00Z"));

        assertThat(analyses.countByFile_Id(imageId)).isEqualTo(1);
        assertThat(analyses.countByFile_Id(noteId)).isZero();
    }

    @Test
    void onlyTheOwnerCanExplicitlyRetryAFailedAnalysis() throws Exception {
        MockHttpSession emma = login("emma");
        MockHttpSession alex = login("alex");
        String projectId = project(emma);
        Upload image = upload(emma, "retry.jpg", "image/jpeg", "retry-image".getBytes());
        MvcResult created = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "analysis-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Retry image\",\"uploadIntentIds\":[\"%s\"]}"
                                .formatted(projectId, image.id())))
                .andExpect(status().isCreated()).andReturn();
        UUID fileId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(),
                "$.version.snapshot.content.files[0].id"));
        ImageAnalysis analysis = analyses.findByFile_Id(fileId).orElseThrow();
        Instant now = analysis.getNextAttemptTime();
        for (int attempt = 0; attempt < 3; attempt++) {
            analysis.claim(now);
            analysis.recordFailure(now, 3, Duration.ofSeconds(1));
            analysis = analyses.saveAndFlush(analysis);
            now = analysis.getNextAttemptTime();
        }
        assertThat(analysis.getStatus()).isEqualTo(ImageAnalysisStatus.FAILED);

        mvc.perform(post("/api/v1/files/{id}/analysis/retry", fileId).session(alex)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());

        MvcResult retried = mvc.perform(post("/api/v1/files/{id}/analysis/retry", fileId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<String>read(retried.getResponse().getContentAsString(), "$.status"))
                .isEqualTo("PENDING");
        assertThat(JsonPath.<Integer>read(retried.getResponse().getContentAsString(), "$.attemptCount"))
                .isZero();
    }

    @Test
    void laterFileVersionsCreateAnalysisOnlyForNewImageAssets() throws Exception {
        MockHttpSession emma = login("emma");
        String projectId = project(emma);
        Upload firstImage = upload(emma, "first.jpg", "image/jpeg", "first-image".getBytes());
        MvcResult first = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "analysis-version-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Versioned images\",\"uploadIntentIds\":[\"%s\"]}"
                                .formatted(projectId, firstImage.id())))
                .andExpect(status().isCreated()).andReturn();
        String firstJson = first.getResponse().getContentAsString();
        UUID materialId = UUID.fromString(JsonPath.read(firstJson, "$.id"));
        UUID firstFileId = UUID.fromString(JsonPath.read(firstJson, "$.version.snapshot.content.files[0].id"));
        Upload secondImage = upload(emma, "second.png", "image/png", "second-image".getBytes());

        MvcResult second = mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "analysis-version-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Versioned images\",\"files\":[" +
                                "{\"kind\":\"EXISTING\",\"fileId\":\"" + firstFileId + "\"}," +
                                "{\"kind\":\"UPLOAD\",\"uploadIntentId\":\"" + secondImage.id() + "\"}]}"))
                .andExpect(status().isCreated()).andReturn();
        UUID secondFileId = UUID.fromString(JsonPath.read(second.getResponse().getContentAsString(),
                "$.version.snapshot.content.files[1].id"));

        assertThat(analyses.countByFile_Id(firstFileId)).isEqualTo(1);
        assertThat(analyses.countByFile_Id(secondFileId)).isEqualTo(1);
        assertThat(analyses.findByFile_Id(secondFileId).orElseThrow().getStatus())
                .isEqualTo(ImageAnalysisStatus.PENDING);
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String project(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Analysis project\"}"))
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

    private record Upload(String id) {
    }
}
