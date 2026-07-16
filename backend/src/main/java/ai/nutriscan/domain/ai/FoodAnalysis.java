package ai.nutriscan.domain.ai;

import java.math.BigDecimal;
import java.util.List;

/** Resultado del análisis de visión: plato, ingredientes y nutrición estimada. */
public record FoodAnalysis(
        String dishName,
        String portion,
        BigDecimal confidence,
        Nutrition total,
        List<DetectedItem> items) {

    public record DetectedItem(String name, String quantity, Nutrition nutrition) {}

    public record Nutrition(
            BigDecimal calories,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatG,
            BigDecimal fiberG,
            BigDecimal sodiumMg,
            BigDecimal sugarG) {}
}
