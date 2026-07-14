package ai.nutriscan.domain.repository;

import ai.nutriscan.domain.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
