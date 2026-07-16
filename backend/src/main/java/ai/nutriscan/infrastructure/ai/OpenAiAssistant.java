package ai.nutriscan.infrastructure.ai;

import ai.nutriscan.domain.ai.NutritionAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Asistente nutricional conversacional (OpenAI chat completions). */
@Component
public class OpenAiAssistant implements NutritionAssistant {

    private final RestClient client;
    private final String model;

    public OpenAiAssistant(@Value("${nutriscan.ai.openai.api-key}") String apiKey,
                           @Value("${nutriscan.ai.openai.model}") String model) {
        this.model = model;
        this.client = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String reply(String userContext, List<Turn> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",
                "content", VisionPrompts.ASSISTANT_SYSTEM_PROMPT + "\n" + userContext));
        for (Turn t : history) {
            messages.add(Map.of("role", "user".equals(t.role()) ? "user" : "assistant",
                    "content", t.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "messages", messages, "max_tokens", 600))
                .retrieve()
                .body(JsonNode.class);

        return response.path("choices").path(0).path("message").path("content").asText();
    }

    @Override
    public String toString() { return "OpenAiAssistant(" + model + ")"; }
}
