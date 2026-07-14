package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan;

    @Column(name = "purchase_token", nullable = false)
    private String purchaseToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType v) { this.plan = v; }
    public String getPurchaseToken() { return purchaseToken; }
    public void setPurchaseToken(String v) { this.purchaseToken = v; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus v) { this.status = v; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
}
