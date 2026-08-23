package com.example.newsmaterial.common.api;

import com.example.newsmaterial.NewsMaterialApplication;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
@Import(FrameworkErrorContractTest.ProbeConfiguration.class)
class FrameworkErrorContractTest {
    private MockMvc mvc;
    private MockHttpSession emma;

    @BeforeEach
    void setUp(WebApplicationContext context) throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"emma\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk()).andReturn();
        emma = (MockHttpSession) login.getRequest().getSession(false);
    }

    @Test
    void malformedJsonAndUnknownEnumUseTheStableEnvelope() throws Exception {
        assertEnvelope(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0]").value(startsWith("body:")));

        assertEnvelope(mvc.perform(post("/api/v1/materials/00000000-0000-0000-0000-000000000001/versions")
                        .session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Idempotency-Key", "bad-enum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCurrentVersion\":1,\"title\":\"Bad\",\"files\":[{\"kind\":\"BOGUS\"}]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidPathAndMissingHeaderNameTheRealInputs() throws Exception {
        assertEnvelope(mvc.perform(get("/api/v1/materials/not-a-uuid").session(emma)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("id:")));

        assertEnvelope(mvc.perform(post("/api/v1/materials/text")
                        .session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"00000000-0000-0000-0000-000000000001\",\"title\":\"Note\",\"textBody\":\"Body\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("Idempotency-Key:")));
    }

    @Test
    void bodyMethodAndMissingParameterValidationExposeFieldSpecificDetails() throws Exception {
        assertEnvelope(mvc.perform(post("/api/v1/projects")
                        .session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("title:")));

        assertEnvelope(mvc.perform(get("/api/v1/error-contract/missing").session(emma)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("required:")));

        assertEnvelope(mvc.perform(get("/api/v1/error-contract/constraint")
                        .session(emma).queryParam("value", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("value:")));
    }

    private org.springframework.test.web.servlet.ResultActions assertEnvelope(
            org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$", hasKey("code")))
                .andExpect(jsonPath("$", hasKey("message")))
                .andExpect(jsonPath("$", hasKey("requestId")))
                .andExpect(jsonPath("$", hasKey("details")))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/error-contract/missing")
        void missing(@RequestParam String required) {
        }

        @GetMapping("/api/v1/error-contract/constraint")
        void constraint(@RequestParam @NotBlank String value) {
        }
    }
}
