package ai.nutriscan.domain.repository;

import ai.nutriscan.domain.model.WaterLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {
    List<WaterLog> findByUserIdAndLogDateOrderByLoggedAtAsc(UUID userId, LocalDate date);

    @Query("select coalesce(sum(w.ml), 0) from WaterLog w where w.user.id = :userId and w.logDate = :date")
    int totalMlForDate(@Param("userId") UUID userId, @Param("date") LocalDate date);
}
