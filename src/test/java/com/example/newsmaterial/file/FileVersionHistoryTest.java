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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class FileVersionHistoryTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void ownerCanReuseAndReplaceFilesWhileOldOrderedSnapshotStaysImmutable() throws Exception {
        MockHttpSession emma = login("emma");
        String project = project(emma);
        Upload first = upload(emma, "first.txt", "first");
        Upload second = upload(emma, "second.txt", "second");
        MvcResult v1 = create(emma, project, first.id(), second.id());
        String materialId = JsonPath.read(v1.getResponse().getContentAsString(), "$.id");
        String firstFile = JsonPath.read(v1.getResponse().getContentAsString(), "$.version.snapshot.content.files[0].id");
        String secondFile = JsonPath.read(v1.getResponse().getContentAsString(), "$.version.snapshot.content.files[1].id");
        Upload replacement = upload(emma, "replacement.txt", "replacement");

        MvcResult v2 = mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key", "file-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Updated files\",\"textContent\":null,\"fileIds\":[\"%s\"],\"uploadIntentIds\":[\"%s\"]}".formatted(secondFile, replacement.id())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version.versionNumber").value(2)).andReturn();
        String replacementFile = JsonPath.read(v2.getResponse().getContentAsString(), "$.version.snapshot.content.files[1].id");
        assertThat(replacementFile).isNotEqualTo(firstFile);

        mvc.perform(get("/api/v1/materials/{id}/versions/{number}", materialId, 1).session(login("alex")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historical").value(true))
                .andExpect(jsonPath("$.version.snapshot.content.files[0].id").value(firstFile))
                .andExpect(jsonPath("$.version.snapshot.content.files[1].id").value(secondFile));
        mvc.perform(get("/api/v1/materials/{id}", materialId).session(emma))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version.snapshot.content.files[0].id").value(secondFile))
                .andExpect(jsonPath("$.version.snapshot.content.files[1].id").value(replacementFile));

        MvcResult replay = mvc.perform(post("/api/v1/materials/{id}/versions", materialId).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key", "file-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Updated files\",\"textContent\":null,\"fileIds\":[\"%s\"],\"uploadIntentIds\":[\"%s\"]}".formatted(secondFile, replacement.id())))
                .andExpect(status().isCreated()).andReturn();
        assertThat(JsonPath.<Integer>read(replay.getResponse().getContentAsString(), "$.version.versionNumber")).isEqualTo(2);
    }

    @Test void nonOwnerCannotVersionAndCurrentFileFromAnotherMaterialCannotBeAttached() throws Exception {
        MockHttpSession emma = login("emma");
        String project = project(emma);
        Upload first = upload(emma, "one.txt", "one");
        MvcResult one = create(emma, project, first.id());
        String materialOne = JsonPath.read(one.getResponse().getContentAsString(), "$.id");
        String fileOne = JsonPath.read(one.getResponse().getContentAsString(), "$.version.snapshot.content.files[0].id");
        Upload second = upload(emma, "two.txt", "two");
        MvcResult two = create(emma, project, second.id());
        String materialTwo = JsonPath.read(two.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/materials/{id}/versions", materialOne).session(login("alex"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key", "alex-file-v2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedCurrentVersion\":1,\"title\":\"No\",\"fileIds\":[\"%s\"],\"uploadIntentIds\":[]}".formatted(fileOne)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/materials/{id}/versions", materialTwo).session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key", "cross-material")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedCurrentVersion\":1,\"title\":\"No\",\"fileIds\":[\"%s\"],\"uploadIntentIds\":[]}".formatted(fileOne)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_FILE_SET"));
    }

    private MvcResult create(MockHttpSession session, String project, String... ids) throws Exception {
        String joined = java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        return mvc.perform(post("/api/v1/materials/file").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "create-" + java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Files\",\"uploadIntentIds\":[%s]}".formatted(project, joined)))
                .andExpect(status().isCreated()).andReturn();
    }
    private Upload upload(MockHttpSession session, String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        MvcResult result = mvc.perform(post("/api/v1/uploads/intents").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[{\"originalFilename\":\"%s\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}".formatted(name, bytes.length, sha(bytes))))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("text/plain").content(bytes)).andExpect(status().isNoContent());
        return new Upload(id);
    }
    private String project(MockHttpSession session) throws Exception { MvcResult r = mvc.perform(post("/api/v1/projects").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Version files\"}")).andExpect(status().isCreated()).andReturn(); return JsonPath.read(r.getResponse().getContentAsString(), "$.id"); }
    private MockHttpSession login(String username) throws Exception { return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username))).andExpect(status().isOk()).andReturn().getRequest().getSession(false); }
    private String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private record Upload(String id) {}
}
