package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.ProfileDtos.ProfileResponse;
import ai.nutriscan.application.dto.ProfileDtos.UpdateProfileRequest;
import ai.nutriscan.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final CurrentUserService currentUser;
    private final NutritionCalculator calculator;

    public ProfileService(CurrentUserService currentUser, NutritionCalculator calculator) {
        this.currentUser = currentUser;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return ProfileResponse.from(currentUser.require());
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest req) {
        User u = currentUser.require();
        if (req.name() != null) u.setName(req.name());
        if (req.age() != null) u.setAge(req.age());
        if (req.sex() != null) u.setSex(req.sex());
        if (req.weightKg() != null) u.setWeightKg(req.weightKg());
        if (req.heightCm() != null) u.setHeightCm(req.heightCm());
        if (req.activityLevel() != null) u.setActivityLevel(req.activityLevel());
        if (req.goal() != null) u.setGoal(req.goal());
        if (req.targetWeightKg() != null) u.setTargetWeightKg(req.targetWeightKg());

        // Recalcular calorías objetivo cuando el perfil está completo
        if (u.getSex() != null && u.getAge() != null && u.getWeightKg() != null
                && u.getHeightCm() != null && u.getActivityLevel() != null && u.getGoal() != null) {
            u.setTargetCalories(calculator.targetCalories(u.getSex(), u.getAge(),
                    u.getWeightKg().doubleValue(), u.getHeightCm().doubleValue(),
                    u.getActivityLevel(), u.getGoal()));
        }
        return ProfileResponse.from(u);
    }
}
