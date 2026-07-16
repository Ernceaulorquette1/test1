package ai.nutriscan.infrastructure.ai;

import ai.nutriscan.domain.ai.FoodAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Convierte la respuesta JSON del modelo de visión en un FoodAnalysis. */
final class JsonAnalysisParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonAnalysisParser() {}

    static FoodAnalysis parse(String raw) {
        try {
            // Los modelos a veces envuelven el JSON en un bloque markdown
            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "");
            }
            JsonNode root = MAPPER.readTree(json);
            List<FoodAnalysis.DetectedItem> items = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                items.add(new FoodAnalysis.DetectedItem(
                        item.path("name").asText(),
                        item.path("quantity").asText(),
                        nutrition(item.path("nutrition"))));
            }
            return new FoodAnalysis(
                    root.path("dishName").asText("Desconocido"),
                    root.path("portion").asText(""),
                    BigDecimal.valueOf(root.path("confidence").asDouble(0)),
                    nutrition(root.path("total")),
                    items);
        } catch (Exception e) {
            throw new IllegalStateException("Respuesta del modelo de visión no parseable", e);
        }
    }

    private static FoodAnalysis.Nutrition nutrition(JsonNode n) {
        return new FoodAnalysis.Nutrition(
                dec(n, "calories"), dec(n, "proteinG"), dec(n, "carbsG"),
                dec(n, "fatG"), dec(n, "fiberG"), dec(n, "sodiumMg"), dec(n, "sugarG"));
    }

    private static BigDecimal dec(JsonNode n, String field) {
        return BigDecimal.valueOf(n.path(field).asDouble(0));
    }
}
