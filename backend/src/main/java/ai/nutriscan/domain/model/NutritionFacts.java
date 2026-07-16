package ai.nutriscan.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Valores nutricionales de una comida o ingrediente. */
@Embeddable
public class NutritionFacts {

    @Column(name = "calories")  private BigDecimal calories = BigDecimal.ZERO;
    @Column(name = "protein_g") private BigDecimal proteinG = BigDecimal.ZERO;
    @Column(name = "carbs_g")   private BigDecimal carbsG = BigDecimal.ZERO;
    @Column(name = "fat_g")     private BigDecimal fatG = BigDecimal.ZERO;
    @Column(name = "fiber_g")   private BigDecimal fiberG = BigDecimal.ZERO;
    @Column(name = "sodium_mg") private BigDecimal sodiumMg = BigDecimal.ZERO;
    @Column(name = "sugar_g")   private BigDecimal sugarG = BigDecimal.ZERO;

    public NutritionFacts() {}

    public NutritionFacts(BigDecimal calories, BigDecimal proteinG, BigDecimal carbsG,
                          BigDecimal fatG, BigDecimal fiberG, BigDecimal sodiumMg, BigDecimal sugarG) {
        this.calories = calories; this.proteinG = proteinG; this.carbsG = carbsG;
        this.fatG = fatG; this.fiberG = fiberG; this.sodiumMg = sodiumMg; this.sugarG = sugarG;
    }

    public BigDecimal getCalories() { return calories; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public BigDecimal getFiberG() { return fiberG; }
    public BigDecimal getSodiumMg() { return sodiumMg; }
    public BigDecimal getSugarG() { return sugarG; }

    public void setCalories(BigDecimal v) { this.calories = v; }
    public void setProteinG(BigDecimal v) { this.proteinG = v; }
    public void setCarbsG(BigDecimal v) { this.carbsG = v; }
    public void setFatG(BigDecimal v) { this.fatG = v; }
    public void setFiberG(BigDecimal v) { this.fiberG = v; }
    public void setSodiumMg(BigDecimal v) { this.sodiumMg = v; }
    public void setSugarG(BigDecimal v) { this.sugarG = v; }
}
