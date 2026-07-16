package ai.nutriscan.application.service;

import ai.nutriscan.domain.model.ActivityLevel;
import ai.nutriscan.domain.model.Goal;
import ai.nutriscan.domain.model.Sex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionCalculatorTest {

    private final NutritionCalculator calc = new NutritionCalculator();

    @Test
    void hombreModeradoQuePierdePeso() {
        // BMR Mifflin-St Jeor: 10*70 + 6.25*175 - 5*28 + 5 = 1658.75; TDEE *1.55 = 2571; -500 = 2071
        int kcal = calc.targetCalories(Sex.MALE, 28, 70, 175, ActivityLevel.MODERATE, Goal.LOSE_WEIGHT);
        assertThat(kcal).isEqualTo(2071);
    }

    @Test
    void mujerSedentariaMantiene() {
        // BMR: 10*60 + 6.25*165 - 5*30 - 161 = 1320.25; TDEE *1.2 = 1584
        int kcal = calc.targetCalories(Sex.FEMALE, 30, 60, 165, ActivityLevel.SEDENTARY, Goal.MAINTAIN);
        assertThat(kcal).isEqualTo(1584);
    }

    @Test
    void ganarMusculoAgregaSuperavit() {
        int maintain = calc.targetCalories(Sex.MALE, 25, 80, 180, ActivityLevel.ACTIVE, Goal.MAINTAIN);
        int gain = calc.targetCalories(Sex.MALE, 25, 80, 180, ActivityLevel.ACTIVE, Goal.GAIN_MUSCLE);
        assertThat(gain - maintain).isEqualTo(300);
    }

    @Test
    void nuncaBajaDelMinimoSeguro() {
        int kcal = calc.targetCalories(Sex.FEMALE, 70, 40, 150, ActivityLevel.SEDENTARY, Goal.LOSE_WEIGHT);
        assertThat(kcal).isGreaterThanOrEqualTo(1200);
    }
}
