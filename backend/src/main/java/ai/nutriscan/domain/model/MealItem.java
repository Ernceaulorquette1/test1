package ai.nutriscan.domain.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "meal_items")
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id")
    private Meal meal;

    @Column(nullable = false)
    private String name;

    private String quantity;

    @Embedded
    private NutritionFacts nutrition = new NutritionFacts();

    public UUID getId() { return id; }
    public Meal getMeal() { return meal; }
    public void setMeal(Meal v) { this.meal = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String v) { this.quantity = v; }
    public NutritionFacts getNutrition() { return nutrition; }
    public void setNutrition(NutritionFacts v) { this.nutrition = v; }
}
