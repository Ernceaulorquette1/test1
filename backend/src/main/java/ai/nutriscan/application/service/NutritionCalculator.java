package ai.nutriscan.application.service;

import ai.nutriscan.domain.model.ActivityLevel;
import ai.nutriscan.domain.model.Goal;
import ai.nutriscan.domain.model.Sex;
import org.springframework.stereotype.Component;

/**
 * Cálculo de calorías objetivo según Mifflin-St Jeor (estándar clínico),
 * ajustado por nivel de actividad y objetivo del usuario.
 */
@Component
public class NutritionCalculator {

    public int targetCalories(Sex sex, int age, double weightKg, double heightCm,
                              ActivityLevel activity, Goal goal) {
        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + (sex == Sex.MALE ? 5 : -161);
        double tdee = bmr * activityFactor(activity);
        double target = switch (goal) {
            case LOSE_WEIGHT -> tdee - 500;   // déficit ~0,5 kg/semana
            case MAINTAIN -> tdee;
            case GAIN_MUSCLE -> tdee + 300;   // superávit moderado
        };
        return (int) Math.max(1200, Math.round(target)); // mínimo seguro
    }

    private double activityFactor(ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
            case VERY_ACTIVE -> 1.9;
        };
    }
}
