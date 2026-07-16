package ai.nutriscan.application.dto;

import ai.nutriscan.domain.model.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record UpdateProfileRequest(
            @Size(max = 120) String name,
            @Min(10) @Max(120) Integer age,
            Sex sex,
            @DecimalMin("20") @DecimalMax("400") BigDecimal weightKg,
            @DecimalMin("90") @DecimalMax("250") BigDecimal heightCm,
            ActivityLevel activityLevel,
            Goal goal,
            @DecimalMin("20") @DecimalMax("400") BigDecimal targetWeightKg) {}

    public record ProfileResponse(
            String email, String name, String photoUrl, Integer age, Sex sex,
            BigDecimal weightKg, BigDecimal heightCm, ActivityLevel activityLevel,
            Goal goal, BigDecimal targetWeightKg, Integer targetCalories,
            BigDecimal bmi, boolean premium) {

        public static ProfileResponse from(User u) {
            BigDecimal bmi = null;
            if (u.getWeightKg() != null && u.getHeightCm() != null) {
                double h = u.getHeightCm().doubleValue() / 100.0;
                bmi = BigDecimal.valueOf(Math.round(u.getWeightKg().doubleValue() / (h * h) * 10) / 10.0);
            }
            return new ProfileResponse(u.getEmail(), u.getName(), u.getPhotoUrl(), u.getAge(), u.getSex(),
                    u.getWeightKg(), u.getHeightCm(), u.getActivityLevel(), u.getGoal(),
                    u.getTargetWeightKg(), u.getTargetCalories(), bmi, u.isPremium());
        }
    }
}
