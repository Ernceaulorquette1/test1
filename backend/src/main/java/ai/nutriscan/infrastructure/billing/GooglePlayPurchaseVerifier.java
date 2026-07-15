package ai.nutriscan.infrastructure.billing;

import ai.nutriscan.domain.billing.PlayPurchaseVerifier;
import ai.nutriscan.domain.model.PlanType;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Verificación real contra la API Google Play Developer
 * (purchases.subscriptionsv2.get). Requiere una service account con el rol
 * "Ver información financiera" vinculada en Play Console y el scope
 * androidpublisher. Activa con nutriscan.billing.mode=play.
 */
@Component
@ConditionalOnProperty(name = "nutriscan.billing.mode", havingValue = "play")
public class GooglePlayPurchaseVerifier implements PlayPurchaseVerifier {

    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

    private final RestClient client;
    private final String packageName;

    public GooglePlayPurchaseVerifier(
            @Value("${nutriscan.billing.package-name}") String packageName) {
        this.packageName = packageName;
        this.client = RestClient.builder()
                .baseUrl("https://androidpublisher.googleapis.com/androidpublisher/v3")
                .build();
    }

    @Override
    public VerifiedPurchase verify(PlanType plan, String purchaseToken) {
        try {
            var credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPE);
            credentials.refreshIfExpired();

            JsonNode sub = client.get()
                    .uri("/applications/{pkg}/purchases/subscriptionsv2/tokens/{token}",
                            packageName, purchaseToken)
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(JsonNode.class);

            String state = sub.path("subscriptionState").asText();
            if (!"SUBSCRIPTION_STATE_ACTIVE".equals(state)
                    && !"SUBSCRIPTION_STATE_IN_GRACE_PERIOD".equals(state)) {
                throw new InvalidPurchaseException("La suscripción no está activa: " + state);
            }
            String expiry = sub.path("lineItems").path(0).path("expiryTime").asText(null);
            if (expiry == null) {
                throw new InvalidPurchaseException("Respuesta de Google Play sin expiryTime");
            }
            return new VerifiedPurchase(plan, Instant.parse(expiry));
        } catch (InvalidPurchaseException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidPurchaseException("No se pudo verificar la compra con Google Play", e);
        }
    }
}
