package com.example.newsmaterial.file;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class, properties = {
        "app.upload.max-files=2",
        "app.upload.max-file-bytes=5",
        "app.upload.max-total-bytes=100"
})
@ActiveProfiles("local")
class FilePolicyContractTest {
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
    void capabilitiesExposeTheSameConfiguredPolicyUsedByIntents() throws Exception {
        mvc.perform(get("/api/v1/capabilities").session(emma))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload.maxFiles").value(2))
                .andExpect(jsonPath("$.upload.maxFileBytes").value(5))
                .andExpect(jsonPath("$.upload.maxTotalBytes").value(100));

        mvc.perform(post("/api/v1/uploads/intents").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[%s,%s,%s]}".formatted(spec("a", 1), spec("b", 1), spec("c", 1))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));
        mvc.perform(post("/api/v1/uploads/intents").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[%s]}".formatted(spec("large", 6))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void initialAndNextSnapshotsUseTheConfiguredMaximumFileCount() throws Exception {
        String project = project();
        Upload a = upload("a", "a");
        Upload b = upload("b", "b");
        Upload c = upload("c", "c");

        mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Too many\",\"uploadIntentIds\":[\"%s\",\"%s\",\"%s\"]}"
                                .formatted(project, a.id(), b.id(), c.id())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));

        MvcResult created = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Two\",\"uploadIntentIds\":[\"%s\",\"%s\"]}"
                                .formatted(project, a.id(), b.id())))
                .andExpect(status().isCreated()).andReturn();
        String material = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String fileA = JsonPath.read(created.getResponse().getContentAsString(), "$.version.snapshot.content.files[0].id");
        String fileB = JsonPath.read(created.getResponse().getContentAsString(), "$.version.snapshot.content.files[1].id");
        Upload d = upload("d", "d");

        mvc.perform(post("/api/v1/materials/{id}/versions", material).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Three\",\"files\":[{\"kind\":\"EXISTING\",\"fileId\":\"%s\"},{\"kind\":\"EXISTING\",\"fileId\":\"%s\"},{\"kind\":\"UPLOAD\",\"uploadIntentId\":\"%s\"}]}"
                                .formatted(fileA, fileB, d.id())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));
    }

    private String spec(String name, int size) {
        return "{\"originalFilename\":\"%s.txt\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}"
                .formatted(name, size, "a".repeat(64));
    }

    private Upload upload(String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"%s.txt\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(name, bytes.length, checksum)))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(emma).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("text/plain").content(bytes)).andExpect(status().isNoContent());
        return new Upload(id);
    }

    private String project() throws Exception {
        MvcResult project = mvc.perform(post("/api/v1/projects").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Policy\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(project.getResponse().getContentAsString(), "$.id");
    }

    private record Upload(String id) {
    }
}
