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

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class StrictVersionUnionTest {
    private MockMvc mvc;
    private MockHttpSession emma;

    @BeforeEach
    void setUp(WebApplicationContext context) throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        emma = login();
    }

    @Test
    void textRejectsFileFieldsBeforeIdempotencyFingerprinting() throws Exception {
        String material = createText(project());
        String key = "strict-text";
        mvc.perform(version(material, key,
                        "{\"expectedCurrentVersion\":1,\"title\":\"Text v2\",\"textBody\":\"Body\",\"textContent\":\"ignored\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERSION_CONTENT"));

        mvc.perform(version(material, key,
                        "{\"expectedCurrentVersion\":1,\"title\":\"Text v2\",\"textBody\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.versionNumber").value(2));
    }

    @Test
    void fileRejectsTextBodyAndLegacySelectorArrays() throws Exception {
        String project = project();
        Upload upload = upload("one.txt", "one");
        MvcResult created = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Files\",\"uploadIntentIds\":[\"%s\"]}"
                                .formatted(project, upload.id())))
                .andExpect(status().isCreated()).andReturn();
        String material = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String fileId = JsonPath.read(created.getResponse().getContentAsString(), "$.version.snapshot.content.files[0].id");

        mvc.perform(version(material, "file-opposite",
                        "{\"expectedCurrentVersion\":1,\"title\":\"Files v2\",\"textBody\":\"wrong\",\"files\":[{\"kind\":\"EXISTING\",\"fileId\":\"%s\"}]}".formatted(fileId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERSION_CONTENT"));

        mvc.perform(version(material, "file-legacy",
                        "{\"expectedCurrentVersion\":1,\"title\":\"Files v2\",\"fileIds\":[\"%s\"],\"uploadIntentIds\":[]}".formatted(fileId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder version(
            String material, String key, String json) {
        return post("/api/v1/materials/{id}/versions", material).session(emma)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private String createText(String project) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/materials/text").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Text\",\"textBody\":\"v1\"}".formatted(project)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private Upload upload(String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"%s\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(name, bytes.length, sha)))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(emma).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("text/plain").content(bytes))
                .andExpect(status().isNoContent());
        return new Upload(id);
    }

    private String project() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Strict union\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private record Upload(String id) {
    }
}
