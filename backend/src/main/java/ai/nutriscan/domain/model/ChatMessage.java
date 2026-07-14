package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static ChatMessage of(User user, ChatRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.user = user; m.role = role; m.content = content;
        return m;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public ChatRole getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
