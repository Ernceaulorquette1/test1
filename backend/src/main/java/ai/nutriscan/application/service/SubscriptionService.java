package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.MiscDtos.SubscriptionResponse;
import ai.nutriscan.domain.billing.PlayPurchaseVerifier;
import ai.nutriscan.domain.model.*;
import ai.nutriscan.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de suscripciones Premium (Google Play Billing).
 * La validez del purchase token la decide el puerto {@link PlayPurchaseVerifier}:
 * verificación real contra Google Play Developer API en producción
 * (nutriscan.billing.mode=play) o modo desarrollo sin validación externa.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;
    private final PlayPurchaseVerifier verifier;

    public SubscriptionService(SubscriptionRepository subscriptions, CurrentUserService currentUser,
                               PlayPurchaseVerifier verifier) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
        this.verifier = verifier;
    }

    @Transactional
    public SubscriptionResponse verifyPurchase(String plan, String purchaseToken) {
        User user = currentUser.require();
        PlanType planType = PlanType.valueOf(plan.toUpperCase());

        var verified = verifier.verify(planType, purchaseToken);

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(verified.plan());
        sub.setPurchaseToken(purchaseToken);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setExpiresAt(verified.expiresAt());
        subscriptions.save(sub);

        user.setPremiumUntil(verified.expiresAt());
        return new SubscriptionResponse(true, verified.plan().name(), verified.expiresAt());
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse status() {
        User user = currentUser.require();
        String plan = subscriptions.findByUserIdOrderByStartedAtDesc(user.getId()).stream()
                .findFirst().map(s -> s.getPlan().name()).orElse(null);
        return new SubscriptionResponse(user.isPremium(), plan, user.getPremiumUntil());
    }
}
