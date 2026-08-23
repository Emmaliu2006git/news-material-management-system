package com.example.newsmaterial.material;

import com.example.newsmaterial.NewsMaterialApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.context.SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class MaterialInputValidationTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private MockHttpSession emma;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        emma = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void textCreateRejectsValuesThatCannotFitTheVersionSchema() throws Exception {
        assertValidation(post("/api/v1/materials/text")
                .header("Idempotency-Key", "text-title-limit")
                .content(textPayload("\"title\":\"" + "t".repeat(241) + "\",\"textBody\":\"body\"")));
        assertValidation(post("/api/v1/materials/text")
                .header("Idempotency-Key", "text-location-limit")
                .content(textPayload("\"title\":\"title\",\"location\":\"" + "l".repeat(241) + "\",\"textBody\":\"body\"")));
        assertValidation(post("/api/v1/materials/text")
                .header("Idempotency-Key", "text-blank-tag")
                .content(textPayload("\"title\":\"title\",\"manualTags\":[\"  \"],\"textBody\":\"body\"")));
        assertValidation(post("/api/v1/materials/text")
                .header("Idempotency-Key", "text-tag-limit")
                .content(textPayload("\"title\":\"title\",\"manualTags\":[\"" + "g".repeat(81) + "\"],\"textBody\":\"body\"")));
    }

    @Test
    void fileCreateRejectsValuesThatCannotFitTheVersionSchema() throws Exception {
        assertValidation(post("/api/v1/materials/file")
                .header("Idempotency-Key", "file-title-limit")
                .content(textPayload("\"title\":\"" + "t".repeat(241) + "\",\"uploadIntentIds\":[]")));
        assertValidation(post("/api/v1/materials/file")
                .header("Idempotency-Key", "file-location-limit")
                .content(textPayload("\"title\":\"title\",\"location\":\"" + "l".repeat(241) + "\",\"uploadIntentIds\":[]")));
        assertValidation(post("/api/v1/materials/file")
                .header("Idempotency-Key", "file-blank-tag")
                .content(textPayload("\"title\":\"title\",\"manualTags\":[\"  \"],\"uploadIntentIds\":[]")));
        assertValidation(post("/api/v1/materials/file")
                .header("Idempotency-Key", "file-tag-limit")
                .content(textPayload("\"title\":\"title\",\"manualTags\":[\"" + "g".repeat(81) + "\"],\"uploadIntentIds\":[]")));
    }

    @Test
    void versionEndpointRejectsSchemaOversizeFieldsBeforeMaterialLookup() throws Exception {
        String missingMaterial = UUID.randomUUID().toString();
        assertValidation(post("/api/v1/materials/{id}/versions", missingMaterial)
                .header("Idempotency-Key", "version-title-limit")
                .content("{\"expectedCurrentVersion\":1,\"title\":\"" + "t".repeat(241) + "\",\"textBody\":\"body\"}"));
        assertValidation(post("/api/v1/materials/{id}/versions", missingMaterial)
                .header("Idempotency-Key", "version-location-limit")
                .content("{\"expectedCurrentVersion\":1,\"title\":\"title\",\"location\":\"" + "l".repeat(241) + "\",\"textBody\":\"body\"}"));
        assertValidation(post("/api/v1/materials/{id}/versions", missingMaterial)
                .header("Idempotency-Key", "version-blank-tag")
                .content("{\"expectedCurrentVersion\":1,\"title\":\"title\",\"manualTags\":[\"  \"],\"textBody\":\"body\"}"));
        assertValidation(post("/api/v1/materials/{id}/versions", missingMaterial)
                .header("Idempotency-Key", "version-tag-limit")
                .content("{\"expectedCurrentVersion\":1,\"title\":\"title\",\"manualTags\":[\"" + "g".repeat(81) + "\"],\"textBody\":\"body\"}"));
    }

    @Test
    void everyMaterialMutationRejectsOversizeIdempotencyKeyBeforePersistence() throws Exception {
        String key = "k".repeat(256);
        assertValidation(post("/api/v1/materials/text")
                .header("Idempotency-Key", key)
                .content(textPayload("\"title\":\"title\",\"textBody\":\"body\"")));
        assertValidation(post("/api/v1/materials/file")
                .header("Idempotency-Key", key)
                .content(textPayload("\"title\":\"title\",\"uploadIntentIds\":[]")));
        assertValidation(post("/api/v1/materials/{id}/versions", UUID.randomUUID())
                .header("Idempotency-Key", key)
                .content("{\"expectedCurrentVersion\":1,\"title\":\"title\",\"textBody\":\"body\"}"));
    }

    private String textPayload(String fields) {
        return "{\"projectId\":\"00000000-0000-0000-0000-000000000001\"," + fields + "}";
    }

    private void assertValidation(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
