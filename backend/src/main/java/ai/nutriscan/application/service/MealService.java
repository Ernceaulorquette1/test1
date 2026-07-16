package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.MealDtos.*;
import ai.nutriscan.domain.ai.FoodAnalysis;
import ai.nutriscan.domain.ai.VisionProvider;
import ai.nutriscan.domain.model.*;
import ai.nutriscan.domain.repository.MealRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class MealService {

    private final MealRepository meals;
    private final CurrentUserService currentUser;
    private final VisionProvider vision;
    private final int freeDailyScanLimit;

    public MealService(MealRepository meals, CurrentUserService currentUser, VisionProvider vision,
                       @Value("${nutriscan.free-tier.daily-scan-limit}") int freeDailyScanLimit) {
        this.meals = meals;
        this.currentUser = currentUser;
        this.vision = vision;
        this.freeDailyScanLimit = freeDailyScanLimit;
    }

    /** Analiza una foto con el proveedor de visión activo, respetando la cuota gratuita. */
    @Transactional(readOnly = true)
    public AnalyzeResponse analyze(byte[] imageBytes, String mimeType) {
        User user = currentUser.require();
        int remaining = remainingFreeScans(user);
        if (!user.isPremium() && remaining <= 0) {
            throw new QuotaExceededException(
                    "Límite diario de análisis alcanzado. Hazte Premium para análisis ilimitados.");
        }
        FoodAnalysis a = vision.analyze(imageBytes, mimeType);
        return new AnalyzeResponse(a.dishName(), a.portion(), a.confidence(),
                toDto(a.total()),
                a.items().stream().map(i -> new MealItemDto(i.name(), i.quantity(), toDto(i.nutrition()))).toList(),
                user.isPremium() ? Integer.MAX_VALUE : Math.max(0, remaining - 1));
    }

    @Transactional
    public MealResponse save(SaveMealRequest req) {
        User user = currentUser.require();
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setMealType(req.mealType());
        meal.setName(req.name());
        meal.setPortion(req.portion());
        meal.setPhotoUrl(req.photoUrl());
        if (req.eatenAt() != null) meal.setEatenAt(req.eatenAt());
        meal.setNutrition(toFacts(req.total()));
        if (req.items() != null) {
            for (MealItemDto dto : req.items()) {
                MealItem item = new MealItem();
                item.setName(dto.name());
                item.setQuantity(dto.quantity());
                item.setNutrition(toFacts(dto.nutrition()));
                meal.addItem(item);
            }
        }
        return MealResponse.from(meals.save(meal));
    }

    @Transactional(readOnly = true)
    public DayHistoryResponse dayHistory(LocalDate date, ZoneId zone) {
        User user = currentUser.require();
        var range = dayRange(date, zone);
        List<Meal> dayMeals = meals.findByUserIdAndEatenAtBetweenOrderByEatenAtAsc(
                user.getId(), range[0], range[1]);

        BigDecimal cal = BigDecimal.ZERO, prot = BigDecimal.ZERO, carb = BigDecimal.ZERO,
                fat = BigDecimal.ZERO, fib = BigDecimal.ZERO, sod = BigDecimal.ZERO, sug = BigDecimal.ZERO;
        for (Meal m : dayMeals) {
            NutritionFacts n = m.getNutrition();
            cal = cal.add(n.getCalories()); prot = prot.add(n.getProteinG());
            carb = carb.add(n.getCarbsG()); fat = fat.add(n.getFatG());
            fib = fib.add(n.getFiberG()); sod = sod.add(n.getSodiumMg()); sug = sug.add(n.getSugarG());
        }
        return new DayHistoryResponse(date.toString(), cal, user.getTargetCalories(),
                new NutritionDto(cal, prot, carb, fat, fib, sod, sug),
                dayMeals.stream().map(MealResponse::from).toList());
    }

    @Transactional
    public void delete(UUID mealId) {
        User user = currentUser.require();
        Meal meal = meals.findByIdAndUserId(mealId, user.getId())
                .orElseThrow(() -> new NotFoundException("Comida no encontrada"));
        meals.delete(meal);
    }

    private int remainingFreeScans(User user) {
        var range = dayRange(LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC);
        long used = meals.countByUserIdAndEatenAtBetween(user.getId(), range[0], range[1]);
        return (int) (freeDailyScanLimit - used);
    }

    private Instant[] dayRange(LocalDate date, ZoneId zone) {
        return new Instant[]{
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant()};
    }

    private NutritionDto toDto(FoodAnalysis.Nutrition n) {
        return new NutritionDto(n.calories(), n.proteinG(), n.carbsG(), n.fatG(),
                n.fiberG(), n.sodiumMg(), n.sugarG());
    }

    private NutritionFacts toFacts(NutritionDto d) {
        return new NutritionFacts(nz(d.calories()), nz(d.proteinG()), nz(d.carbsG()),
                nz(d.fatG()), nz(d.fiberG()), nz(d.sodiumMg()), nz(d.sugarG()));
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
