package com.example.newsmaterial.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.analysis.provider", havingValue = "openai")
public class OpenAiImageAnalysisProvider implements ImageAnalysisProvider {
    private final RestClient client;
    private final ObjectMapper json;
    private final String model;

    public OpenAiImageAnalysisProvider(RestClient.Builder builder,
                                       ObjectMapper json,
                                       @Value("${app.analysis.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                       @Value("${app.analysis.openai.api-key:}") String apiKey,
                                       @Value("${app.analysis.openai.model:gpt-5-mini}") String model,
                                       @Value("${app.analysis.openai.connect-timeout:PT3S}") Duration connectTimeout,
                                       @Value("${app.analysis.openai.request-timeout:PT20S}") Duration requestTimeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is required when the OpenAI analysis provider is enabled");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        this.client = builder.requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.json = json;
        this.model = model;
    }

    @Override
    public ImageAnalysisResult analyze(byte[] imageBytes, String mimeType) {
        String imageUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("description", "tags"),
                "properties", Map.of(
                        "description", Map.of("type", "string", "maxLength", 4000),
                        "tags", Map.of("type", "array", "maxItems", 20,
                                "items", Map.of("type", "string", "maxLength", 80))));
        Map<String, Object> request = Map.of(
                "model", model,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "input_text",
                                        "text", "Describe this news image factually and provide concise searchable tags."),
                                Map.of("type", "input_image", "image_url", imageUrl)))),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "image_analysis",
                        "strict", true,
                        "schema", schema)));
        String response = client.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        return parseResponse(json, response);
    }

    static ImageAnalysisResult parseResponse(ObjectMapper json, String responseBody) {
        try {
            JsonNode root = json.readTree(responseBody);
            String outputText = null;
            for (JsonNode output : root.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                        outputText = content.path("text").asText();
                        break;
                    }
                }
                if (outputText != null) {
                    break;
                }
            }
            if (outputText == null) {
                throw new IllegalArgumentException("AI response did not contain structured output");
            }
            JsonNode result = json.readTree(outputText);
            if (!result.path("description").isTextual() || !result.path("tags").isArray()) {
                throw new IllegalArgumentException("AI response did not match the required schema");
            }
            List<String> tags = new ArrayList<>();
            result.path("tags").forEach(tag -> {
                if (!tag.isTextual()) {
                    throw new IllegalArgumentException("AI tags must be strings");
                }
                tags.add(tag.asText());
            });
            return new ImageAnalysisResult(result.path("description").asText(), tags);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI response could not be parsed", exception);
        }
    }
}
