package ai.nutriscan.application.dto;

import java.math.BigDecimal;
import java.util.List;

public final class StatsDtos {
    private StatsDtos() {}

    public record DailyPoint(String date, BigDecimal calories, BigDecimal proteinG,
                             BigDecimal carbsG, BigDecimal fatG) {}

    public record WeightPoint(String date, BigDecimal weightKg) {}

    public record StatsResponse(BigDecimal currentWeightKg, BigDecimal targetWeightKg, BigDecimal bmi,
                                Integer targetCalories, BigDecimal avgCalories,
                                List<DailyPoint> calorieSeries, List<WeightPoint> weightSeries) {}

    public record LogWeightRequest(BigDecimal weightKg) {}
}
