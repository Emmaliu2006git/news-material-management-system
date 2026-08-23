package com.example.newsmaterial.storage;

import com.example.newsmaterial.NewsMaterialApplication;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NewsMaterialApplication.class)
@ActiveProfiles("local")
class LocalStorageSecurityTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void localUploadRequiresAndAcceptsTheRealSessionCsrfToken() throws Exception {
        MockHttpSession emma = login();
        byte[] body = "local upload".getBytes();
        MvcResult intent = mvc.perform(post("/api/v1/uploads/intents").session(emma)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[{\"originalFilename\":\"local.txt\",\"mimeType\":\"text/plain\",\"sizeBytes\":%d,\"checksumSha256\":\"%s\"}]}"
                                .formatted(body.length, sha(body))))
                .andExpect(status().isCreated()).andReturn();
        String uploadUrl = JsonPath.read(intent.getResponse().getContentAsString(), "$.files[0].uploadUrl");

        mvc.perform(put(uploadUrl).session(emma).contentType("text/plain").content(body))
                .andExpect(status().isForbidden());

        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf").session(emma))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        String headerName = JsonPath.read(csrf.getResponse().getContentAsString(), "$.headerName");
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(put(uploadUrl).session(emma).cookie(cookie).header(headerName, token)
                        .contentType("text/plain").content(body))
                .andExpect(status().isNoContent());
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
}
