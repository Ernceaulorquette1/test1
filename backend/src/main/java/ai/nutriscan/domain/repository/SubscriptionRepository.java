package ai.nutriscan.domain.repository;

import ai.nutriscan.domain.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByUserIdOrderByStartedAtDesc(UUID userId);
}
