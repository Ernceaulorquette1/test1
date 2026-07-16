package ai.nutriscan.infrastructure.ai;

import ai.nutriscan.domain.ai.FoodAnalysis;
import ai.nutriscan.domain.ai.VisionProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Implementación del puerto de visión sobre la API de OpenAI (gpt-4o). */
@Component("openaiVision")
public class OpenAiVisionProvider implements VisionProvider {

    private final RestClient client;
    private final String model;

    public OpenAiVisionProvider(@Value("${nutriscan.ai.openai.api-key}") String apiKey,
                                @Value("${nutriscan.ai.openai.model}") String model) {
        this.model = model;
        this.client = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public FoodAnalysis analyze(byte[] imageBytes, String mimeType) {
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", VisionPrompts.FOOD_ANALYSIS_PROMPT),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));

        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String content = response.path("choices").path(0).path("message").path("content").asText();
        return JsonAnalysisParser.parse(content);
    }

    @Override
    public String id() { return "openai"; }
}
