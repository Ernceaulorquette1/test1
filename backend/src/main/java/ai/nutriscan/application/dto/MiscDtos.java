package ai.nutriscan.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MiscDtos {
    private MiscDtos() {}

    // ---- Agua ----
    public record AddWaterRequest(@Min(1) @Max(3000) int ml) {}
    public record WaterDayResponse(String date, int totalMl, int goalMl,
                                   List<WaterEntry> entries) {}
    public record WaterEntry(Instant loggedAt, int ml) {}

    // ---- Chat IA ----
    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {}
    public record ChatResponse(String reply) {}
    public record ChatHistoryEntry(String role, String content, Instant createdAt) {}

    // ---- Código de barras ----
    public record BarcodeProductResponse(String barcode, String name, String brand, String imageUrl,
                                         BigDecimal caloriesPer100g, BigDecimal proteinG, BigDecimal carbsG,
                                         BigDecimal fatG, BigDecimal fiberG, BigDecimal sodiumMg, BigDecimal sugarG) {}

    // ---- Suscripciones ----
    public record VerifyPurchaseRequest(@NotBlank String plan, @NotBlank String purchaseToken) {}
    public record SubscriptionResponse(boolean premium, String plan, Instant expiresAt) {}
}
