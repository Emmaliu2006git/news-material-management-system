package com.example.newsmaterial.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageAnalysisProviderTest {
    @Test
    void localProviderIsDeterministicForTheSameImage() {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider();

        ImageAnalysisResult first = provider.analyze("same-image".getBytes(), "image/jpeg");
        ImageAnalysisResult second = provider.analyze("same-image".getBytes(), "image/jpeg");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void openAiResponseParserAcceptsOnlyStructuredDescriptionAndTags() {
        ObjectMapper json = new ObjectMapper();
        String response = """
                {"output":[{"content":[{"type":"output_text","text":"{\\\"description\\\":\\\"Flooded road\\\",\\\"tags\\\":[\\\"flood\\\",\\\"road\\\"]}"}]}]}
                """;

        ImageAnalysisResult result = OpenAiImageAnalysisProvider.parseResponse(json, response);

        assertThat(result.description()).isEqualTo("Flooded road");
        assertThat(result.tags()).containsExactly("flood", "road");
        assertThatThrownBy(() -> OpenAiImageAnalysisProvider.parseResponse(json,
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{}\"}]}]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
