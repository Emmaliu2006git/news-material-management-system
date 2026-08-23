package com.example.newsmaterial.file;

import com.example.newsmaterial.NewsMaterialApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class FileMaterialApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void createsOrderedMultiFileSnapshotWithOptionalTextAndAuthenticatedDownload() throws Exception {
        MockHttpSession emma = login("emma");
        String project = project(emma);
        Upload first = upload(emma, "note.txt", "text/plain", "first".getBytes());
        Upload second = upload(emma, "photo.jpg", "image/jpeg", "second".getBytes());
        MvcResult created = create(emma, project, "Flood evidence", "Reporter context", first.id(), second.id(), "file-create");
        String body = created.getResponse().getContentAsString();
        String materialId = JsonPath.read(body, "$.id");
        String firstFileId = JsonPath.read(body, "$.version.snapshot.content.files[0].id");
        assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("FILE");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.kind")).isEqualTo("FILE");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.textContent")).isEqualTo("Reporter context");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.files[0].originalFilename")).isEqualTo("note.txt");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.files[0].uploaderUsername")).isEqualTo("emma");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.files[1].originalFilename")).isEqualTo("photo.jpg");
        assertThat(JsonPath.<String>read(body, "$.version.snapshot.content.files[1].imageAnalysis.status")).isEqualTo("PENDING");
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.version.snapshot.content.files[0]")).doesNotContainKey("objectKey");

        MvcResult replay = create(emma, project, "Flood evidence", "Reporter context", first.id(), second.id(), "file-create");
        assertThat(JsonPath.<String>read(replay.getResponse().getContentAsString(), "$.id")).isEqualTo(materialId);

        MockHttpSession alex = login("alex");
        MvcResult download = mvc.perform(post("/api/v1/files/{id}/download-url", firstFileId).session(alex)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.downloadUrl").exists()).andReturn();
        String url = JsonPath.read(download.getResponse().getContentAsString(), "$.downloadUrl");
        mvc.perform(get(url).session(alex)).andExpect(status().isOk()).andExpect(content().bytes("first".getBytes()));
    }

    @Test void failedObjectVerificationCreatesNoVisibleMaterialAndLeavesIntentRetryable() throws Exception {
        MockHttpSession emma = login("emma");
        String project = project(emma);
        byte[] bytes = "missing".getBytes();
        Upload pending = intent(emma, "missing.txt", "text/plain", bytes);
        long before = jdbc.queryForObject("select count(*) from materials", Long.class);
        mvc.perform(post("/api/v1/materials/file").session(emma).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "missing-object").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(project, "Missing upload", null, pending.id())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_VERIFIED"));
        assertThat(jdbc.queryForObject("select count(*) from materials", Long.class)).isEqualTo(before);
        mvc.perform(put(pending.url()).session(emma).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("text/plain").content(bytes)).andExpect(status().isNoContent());
        create(emma, project, "Missing upload", null, pending.id(), null, "missing-object-retry");
    }

    @Test void consumedIntentCannotFinalizeAnotherMaterialOrVersion() throws Exception {
        MockHttpSession emma = login("emma");
        String project = project(emma);
        Upload upload = upload(emma, "one-use.txt", "text/plain", "one-use".getBytes());
        MvcResult first = create(emma, project, "First use", null, upload.id(), null, "one-use-first");
        String materialId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "one-use-second-material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(project, "Second use", null, upload.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_INTENT_CONSUMED"));

        mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "one-use-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Second use\",\"files\":[{\"kind\":\"UPLOAD\",\"uploadIntentId\":\"%s\"}]}".formatted(upload.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_INTENT_CONSUMED"));
    }

    private MvcResult create(MockHttpSession session, String project, String title, String text, String first, String second, String key) throws Exception {
        return mvc.perform(post("/api/v1/materials/file").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(payload(project, title, text, first, second)))
                .andExpect(status().isCreated()).andReturn();
    }
    private String payload(String project, String title, String text, String... ids) {
        String joined = java.util.Arrays.stream(ids).filter(java.util.Objects::nonNull).map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        return "{\"projectId\":\"%s\",\"title\":\"%s\",\"textContent\":%s,\"uploadIntentIds\":[%s]}".formatted(project, title, text == null ? "null" : "\"" + text + "\"", joined);
    }
    private Upload upload(MockHttpSession session, String name, String mime, byte[] bytes) throws Exception {
        Upload upload = intent(session, name, mime, bytes);
        mvc.perform(put(upload.url()).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(mime).content(bytes))
                .andExpect(status().isNoContent());
        return upload;
    }
    private Upload intent(MockHttpSession session, String name, String mime, byte[] bytes) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/uploads/intents").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[{\"originalFilename\":\"%s\",\"mimeType\":\"%s\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}".formatted(name, mime, bytes.length, sha(bytes))))
                .andExpect(status().isCreated()).andReturn();
        return new Upload(JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].intentId"), JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].uploadUrl"));
    }
    private String project(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"File evidence\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    private MockHttpSession login(String username) throws Exception { return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username))).andExpect(status().isOk()).andReturn().getRequest().getSession(false); }
    private String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private record Upload(String id, String url) {}
}
