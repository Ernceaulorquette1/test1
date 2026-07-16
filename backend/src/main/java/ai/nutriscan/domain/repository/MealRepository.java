package ai.nutriscan.domain.repository;

import ai.nutriscan.domain.model.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MealRepository extends JpaRepository<Meal, UUID> {
    List<Meal> findByUserIdAndEatenAtBetweenOrderByEatenAtAsc(UUID userId, Instant from, Instant to);
    Optional<Meal> findByIdAndUserId(UUID id, UUID userId);
    long countByUserIdAndEatenAtBetween(UUID userId, Instant from, Instant to);
}
