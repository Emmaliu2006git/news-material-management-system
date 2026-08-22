package com.example.newsmaterial.file;

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
import java.util.HexFormat;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class, properties = {
        "app.upload.max-file-bytes=10",
        "app.upload.max-total-bytes=10"
})
@ActiveProfiles("local")
class FileAggregateLimitTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsInitialSnapshotOverAggregateLimitWhenIntentsComeFromSeparateRequests() throws Exception {
        MockHttpSession emma = login();
        String projectId = project(emma);
        Upload first = upload(emma, "first.txt", "123456");
        Upload second = upload(emma, "second.txt", "abcdef");

        mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "aggregate-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Too large\",\"uploadIntentIds\":[\"%s\",\"%s\"]}"
                                .formatted(projectId, first.id(), second.id())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));
    }

    @Test
    void rejectsVersionOverAggregateLimitAcrossRetainedAndNewFiles() throws Exception {
        MockHttpSession emma = login();
        String projectId = project(emma);
        Upload retained = upload(emma, "retained.txt", "123456");
        MvcResult initial = mvc.perform(post("/api/v1/materials/file").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "aggregate-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Initial\",\"uploadIntentIds\":[\"%s\"]}"
                                .formatted(projectId, retained.id())))
                .andExpect(status().isCreated()).andReturn();
        String materialId = JsonPath.read(initial.getResponse().getContentAsString(), "$.id");
        String retainedFileId = JsonPath.read(initial.getResponse().getContentAsString(), "$.version.snapshot.content.files[0].id");
        Upload added = upload(emma, "added.txt", "abcdef");

        mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "aggregate-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Too large\",\"files\":[{\"kind\":\"EXISTING\",\"fileId\":\"%s\"},{\"kind\":\"UPLOAD\",\"uploadIntentId\":\"%s\"}]}"
                                .formatted(retainedFileId, added.id())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));
    }

    private Upload upload(MockHttpSession session, String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"%s\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(name, bytes.length, sha(bytes))))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("text/plain").content(bytes))
                .andExpect(status().isNoContent());
        return new Upload(id);
    }

    private String project(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Aggregate limits\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Upload(String id) {}
}
