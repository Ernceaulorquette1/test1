package ai.nutriscan.web;

import ai.nutriscan.application.dto.MiscDtos.*;
import ai.nutriscan.application.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Premium", description = "Suscripciones vía Google Play Billing")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping("/verify")
    @Operation(summary = "Verifica una compra de Google Play y activa Premium")
    public SubscriptionResponse verify(@Valid @RequestBody VerifyPurchaseRequest req) {
        return subscriptions.verifyPurchase(req.plan(), req.purchaseToken());
    }

    @GetMapping("/status")
    public SubscriptionResponse status() { return subscriptions.status(); }
}
