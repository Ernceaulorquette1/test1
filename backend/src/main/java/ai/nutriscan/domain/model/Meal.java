package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meals")
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(nullable = false)
    private String name;

    @Column(name = "photo_url")
    private String photoUrl;

    private String portion;

    @Column(name = "eaten_at", nullable = false)
    private Instant eatenAt = Instant.now();

    @Embedded
    private NutritionFacts nutrition = new NutritionFacts();

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MealItem> items = new ArrayList<>();

    public void addItem(MealItem item) {
        item.setMeal(this);
        items.add(item);
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType v) { this.mealType = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String v) { this.photoUrl = v; }
    public String getPortion() { return portion; }
    public void setPortion(String v) { this.portion = v; }
    public Instant getEatenAt() { return eatenAt; }
    public void setEatenAt(Instant v) { this.eatenAt = v; }
    public NutritionFacts getNutrition() { return nutrition; }
    public void setNutrition(NutritionFacts v) { this.nutrition = v; }
    public List<MealItem> getItems() { return items; }
}
