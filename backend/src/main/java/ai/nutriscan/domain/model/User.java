package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(name = "photo_url")
    private String photoUrl;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Column(name = "target_weight_kg")
    private BigDecimal targetWeightKg;

    @Column(name = "target_calories")
    private Integer targetCalories;

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** El acceso Premium se determina por la fecha de expiración de la suscripción. */
    public boolean isPremium() {
        return premiumUntil != null && premiumUntil.isAfter(Instant.now());
    }

    public UUID getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String v) { this.firebaseUid = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String v) { this.photoUrl = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { this.age = v; }
    public Sex getSex() { return sex; }
    public void setSex(Sex v) { this.sex = v; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal v) { this.weightKg = v; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal v) { this.heightCm = v; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public void setActivityLevel(ActivityLevel v) { this.activityLevel = v; }
    public Goal getGoal() { return goal; }
    public void setGoal(Goal v) { this.goal = v; }
    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public void setTargetWeightKg(BigDecimal v) { this.targetWeightKg = v; }
    public Integer getTargetCalories() { return targetCalories; }
    public void setTargetCalories(Integer v) { this.targetCalories = v; }
    public Instant getPremiumUntil() { return premiumUntil; }
    public void setPremiumUntil(Instant v) { this.premiumUntil = v; }
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String v) { this.fcmToken = v; }
    public Instant getCreatedAt() { return createdAt; }
}
