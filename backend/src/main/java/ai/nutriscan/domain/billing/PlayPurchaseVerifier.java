package ai.nutriscan.domain.billing;

import ai.nutriscan.domain.model.PlanType;
import java.time.Instant;

/**
 * Puerto de verificación de compras de Google Play Billing.
 * Implementaciones: verificación real contra Google Play Developer API
 * (producción) y modo desarrollo sin validación externa.
 */
public interface PlayPurchaseVerifier {

    record VerifiedPurchase(PlanType plan, Instant expiresAt) {}

    /**
     * Valida un purchase token y devuelve el plan con su fecha de expiración.
     *
     * @throws InvalidPurchaseException si el token no es válido o la compra no está activa
     */
    VerifiedPurchase verify(PlanType plan, String purchaseToken);

    class InvalidPurchaseException extends RuntimeException {
        public InvalidPurchaseException(String message) { super(message); }
        public InvalidPurchaseException(String message, Throwable cause) { super(message, cause); }
    }
}
