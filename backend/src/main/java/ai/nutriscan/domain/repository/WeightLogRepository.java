package ai.nutriscan.domain.repository;

import ai.nutriscan.domain.model.WeightLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeightLogRepository extends JpaRepository<WeightLog, UUID> {
    List<WeightLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(UUID userId, LocalDate from, LocalDate to);
    Optional<WeightLog> findByUserIdAndLogDate(UUID userId, LocalDate date);
}
