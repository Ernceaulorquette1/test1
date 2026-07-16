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

/** Implementación del puerto de visión sobre la API de Google Gemini. */
@Component("geminiVision")
public class GeminiVisionProvider implements VisionProvider {

    private final RestClient client;
    private final String model;
    private final String apiKey;

    public GeminiVisionProvider(@Value("${nutriscan.ai.gemini.api-key}") String apiKey,
                                @Value("${nutriscan.ai.gemini.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Override
    public FoodAnalysis analyze(byte[] imageBytes, String mimeType) {
        Map<String, Object> body = Map.of(
                "generationConfig", Map.of("responseMimeType", "application/json"),
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", VisionPrompts.FOOD_ANALYSIS_PROMPT),
                        Map.of("inlineData", Map.of(
                                "mimeType", mimeType,
                                "data", Base64.getEncoder().encodeToString(imageBytes)))))));

        JsonNode response = client.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String content = response.path("candidates").path(0).path("content")
                .path("parts").path(0).path("text").asText();
        return JsonAnalysisParser.parse(content);
    }

    @Override
    public String id() { return "gemini"; }
}
