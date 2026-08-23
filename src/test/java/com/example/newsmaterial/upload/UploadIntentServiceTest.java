package com.example.newsmaterial.upload;

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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class UploadIntentServiceTest {
    private static final String SHA_A = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void createsOrderedOwnerBoundIntentsWithImmutableKeys() throws Exception {
        MockHttpSession emma = login("emma");
        MvcResult result = mvc.perform(post("/api/v1/uploads/intents").session(emma).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[" + file("a.txt", "text/plain", 1, SHA_A) + "," + file("b.jpg", "image/jpeg", 1, SHA_A) + "]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].uploadUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/local-storage/uploads/")))
                .andExpect(jsonPath("$['files'][0]['requiredHeaders']['Content-Type']").value("text/plain")).andReturn();
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.files[0].objectKey"))
                .isNotEqualTo(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.files[1].objectKey"));
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        mvc.perform(put(url).session(login("alex")).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("text/plain").content("a"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void rejectsCountPerFileAndTotalSizeLimits() throws Exception {
        MockHttpSession emma = login("emma");
        String eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> file("f" + i + ".txt", "text/plain", 1, SHA_A)).collect(java.util.stream.Collectors.joining(","));
        limit(emma, eleven);
        limit(emma, file("large.txt", "text/plain", 52_428_801, SHA_A));
        String total = java.util.stream.IntStream.range(0, 5).mapToObj(i -> file("f" + i + ".mp4", "video/mp4", i == 4 ? 1 : 52_428_800, SHA_A)).collect(java.util.stream.Collectors.joining(","));
        limit(emma, total);
    }

    @Test void rejectsUnsupportedMimeType() throws Exception {
        mvc.perform(post("/api/v1/uploads/intents").session(login("emma")).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[" + file("a.zip", "application/zip", 1, SHA_A) + "]}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test void rejectsMissingUnsafeOrOversizedFileMetadataWithStableClientError() throws Exception {
        MockHttpSession emma = login("emma");
        invalidMetadata(emma, "null");
        invalidMetadata(emma, file("unsafe\\nname.txt", "text/plain", 1, SHA_A));
        invalidMetadata(emma, file("a".repeat(256), "text/plain", 1, SHA_A));
        invalidMetadata(emma, file("a.txt", "text/" + "a".repeat(156), 1, SHA_A));
    }

    private void invalidMetadata(MockHttpSession session, String fileJson) throws Exception {
        mvc.perform(post("/api/v1/uploads/intents").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[" + fileJson + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_REQUEST"));
    }

    @Test void expiredIntentCannotReceiveLocalUpload() throws Exception {
        MockHttpSession emma = login("emma");
        MvcResult result = intent(emma, "expired.txt", "text/plain", 1, SHA_A);
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].intentId");
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.files[0].uploadUrl");
        jdbc.update("update upload_intents set expires_at = ? where id = ?", Instant.EPOCH, UUID.fromString(id));
        mvc.perform(put(url).session(emma).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("text/plain").content("a"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("UPLOAD_INTENT_EXPIRED"));
    }

    private void limit(MockHttpSession session, String files) throws Exception {
        mvc.perform(post("/api/v1/uploads/intents").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[" + files + "]}"))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_LIMIT_EXCEEDED"));
    }
    private MvcResult intent(MockHttpSession session, String name, String mime, long size, String sha) throws Exception {
        return mvc.perform(post("/api/v1/uploads/intents").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[" + file(name, mime, size, sha) + "]}"))
                .andExpect(status().isCreated()).andReturn();
    }
    private String file(String name, String mime, long size, String sha) { return "{\"originalFilename\":\"%s\",\"mimeType\":\"%s\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}".formatted(name, mime, size, sha); }
    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo-password\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
