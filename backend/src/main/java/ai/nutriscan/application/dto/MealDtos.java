package ai.nutriscan.application.dto;

import ai.nutriscan.domain.model.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MealDtos {
    private MealDtos() {}

    public record NutritionDto(BigDecimal calories, BigDecimal proteinG, BigDecimal carbsG,
                               BigDecimal fatG, BigDecimal fiberG, BigDecimal sodiumMg, BigDecimal sugarG) {
        public static NutritionDto from(NutritionFacts n) {
            return new NutritionDto(n.getCalories(), n.getProteinG(), n.getCarbsG(),
                    n.getFatG(), n.getFiberG(), n.getSodiumMg(), n.getSugarG());
        }
    }

    public record MealItemDto(String name, String quantity, NutritionDto nutrition) {}

    public record AnalyzeResponse(String dishName, String portion, BigDecimal confidence,
                                  NutritionDto total, List<MealItemDto> items, int remainingFreeScans) {}

    public record SaveMealRequest(@NotNull MealType mealType, @NotNull String name, String portion,
                                  String photoUrl, Instant eatenAt,
                                  @NotNull NutritionDto total, List<MealItemDto> items) {}

    public record MealResponse(UUID id, MealType mealType, String name, String portion, String photoUrl,
                               Instant eatenAt, NutritionDto total, List<MealItemDto> items) {
        public static MealResponse from(Meal m) {
            return new MealResponse(m.getId(), m.getMealType(), m.getName(), m.getPortion(), m.getPhotoUrl(),
                    m.getEatenAt(), NutritionDto.from(m.getNutrition()),
                    m.getItems().stream()
                            .map(i -> new MealItemDto(i.getName(), i.getQuantity(), NutritionDto.from(i.getNutrition())))
                            .toList());
        }
    }

    /** Historial de un día agrupado por tipo de comida. */
    public record DayHistoryResponse(String date, BigDecimal totalCalories, Integer targetCalories,
                                     NutritionDto totals, List<MealResponse> meals) {}
}
