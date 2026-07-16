package ai.nutriscan.infrastructure.billing;

import ai.nutriscan.domain.billing.PlayPurchaseVerifier;
import ai.nutriscan.domain.model.PlanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Verificador de desarrollo/staging: activa el plan sin llamar a Google Play.
 * NUNCA usar en producción (nutriscan.billing.mode=play en prod).
 */
@Component
@ConditionalOnProperty(name = "nutriscan.billing.mode", havingValue = "dev", matchIfMissing = true)
public class DevPurchaseVerifier implements PlayPurchaseVerifier {

    private static final Logger log = LoggerFactory.getLogger(DevPurchaseVerifier.class);

    @Override
    public VerifiedPurchase verify(PlanType plan, String purchaseToken) {
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new InvalidPurchaseException("Purchase token vacío");
        }
        log.warn("Billing en modo DEV: activando {} sin verificación de Google Play", plan);
        Instant expiresAt = plan == PlanType.MONTHLY
                ? Instant.now().plus(30, ChronoUnit.DAYS)
                : Instant.now().plus(365, ChronoUnit.DAYS);
        return new VerifiedPurchase(plan, expiresAt);
    }
}
