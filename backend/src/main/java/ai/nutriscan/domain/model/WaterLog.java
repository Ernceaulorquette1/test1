package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "water_logs")
public class WaterLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private Integer ml;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate v) { this.logDate = v; }
    public Integer getMl() { return ml; }
    public void setMl(Integer v) { this.ml = v; }
    public Instant getLoggedAt() { return loggedAt; }
}
