package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.StatsDtos.*;
import ai.nutriscan.domain.model.Meal;
import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.model.WeightLog;
import ai.nutriscan.domain.repository.MealRepository;
import ai.nutriscan.domain.repository.WeightLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class StatsService {

    private final MealRepository meals;
    private final WeightLogRepository weights;
    private final CurrentUserService currentUser;

    public StatsService(MealRepository meals, WeightLogRepository weights, CurrentUserService currentUser) {
        this.meals = meals;
        this.weights = weights;
        this.currentUser = currentUser;
    }

    /** Estadísticas para un rango: "week" (7 días), "month" (30) o "year" (365). */
    @Transactional(readOnly = true)
    public StatsResponse forRange(String range) {
        User user = currentUser.require();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int days = switch (range == null ? "week" : range) {
            case "month" -> 30;
            case "year" -> 365;
            default -> 7;
        };
        LocalDate from = today.minusDays(days - 1L);

        List<Meal> rangeMeals = meals.findByUserIdAndEatenAtBetweenOrderByEatenAtAsc(user.getId(),
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (Meal m : rangeMeals) {
            LocalDate d = m.getEatenAt().atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal[] acc = byDay.computeIfAbsent(d, k -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            var n = m.getNutrition();
            acc[0] = acc[0].add(n.getCalories());
            acc[1] = acc[1].add(n.getProteinG());
            acc[2] = acc[2].add(n.getCarbsG());
            acc[3] = acc[3].add(n.getFatG());
        }
        List<DailyPoint> calorieSeries = byDay.entrySet().stream()
                .map(e -> new DailyPoint(e.getKey().toString(), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();

        BigDecimal avg = BigDecimal.ZERO;
        if (!byDay.isEmpty()) {
            BigDecimal sum = byDay.values().stream().map(a -> a[0]).reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(byDay.size()), 0, RoundingMode.HALF_UP);
        }

        List<WeightPoint> weightSeries = weights
                .findByUserIdAndLogDateBetweenOrderByLogDateAsc(user.getId(), from, today).stream()
                .map(w -> new WeightPoint(w.getLogDate().toString(), w.getWeightKg()))
                .toList();

        BigDecimal bmi = null;
        if (user.getWeightKg() != null && user.getHeightCm() != null) {
            double h = user.getHeightCm().doubleValue() / 100.0;
            bmi = BigDecimal.valueOf(Math.round(user.getWeightKg().doubleValue() / (h * h) * 10) / 10.0);
        }
        return new StatsResponse(user.getWeightKg(), user.getTargetWeightKg(), bmi,
                user.getTargetCalories(), avg, calorieSeries, weightSeries);
    }

    @Transactional
    public void logWeight(BigDecimal weightKg) {
        User user = currentUser.require();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        WeightLog log = weights.findByUserIdAndLogDate(user.getId(), today).orElseGet(() -> {
            WeightLog w = new WeightLog();
            w.setUser(user);
            w.setLogDate(today);
            return w;
        });
        log.setWeightKg(weightKg);
        weights.save(log);
        user.setWeightKg(weightKg); // mantener el perfil sincronizado
    }
}
