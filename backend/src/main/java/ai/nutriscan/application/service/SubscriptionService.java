package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.MiscDtos.SubscriptionResponse;
import ai.nutriscan.domain.model.*;
import ai.nutriscan.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Gestión de suscripciones Premium (Google Play Billing).
 *
 * En producción, {@code verifyPurchase} debe validar el purchase token contra
 * la API Google Play Developer (purchases.subscriptions.get) antes de activar
 * el plan; aquí se registra la compra y se activa el período correspondiente,
 * dejando el punto de integración claramente delimitado.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final CurrentUserService currentUser;

    public SubscriptionService(SubscriptionRepository subscriptions, CurrentUserService currentUser) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
    }

    @Transactional
    public SubscriptionResponse verifyPurchase(String plan, String purchaseToken) {
        User user = currentUser.require();
        PlanType planType = PlanType.valueOf(plan.toUpperCase());

        // TODO producción: validar purchaseToken con Google Play Developer API.
        Instant expiresAt = planType == PlanType.MONTHLY
                ? Instant.now().plus(30, ChronoUnit.DAYS)
                : Instant.now().plus(365, ChronoUnit.DAYS);

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(planType);
        sub.setPurchaseToken(purchaseToken);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setExpiresAt(expiresAt);
        subscriptions.save(sub);

        user.setPremiumUntil(expiresAt);
        return new SubscriptionResponse(true, planType.name(), expiresAt);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse status() {
        User user = currentUser.require();
        String plan = subscriptions.findByUserIdOrderByStartedAtDesc(user.getId()).stream()
                .findFirst().map(s -> s.getPlan().name()).orElse(null);
        return new SubscriptionResponse(user.isPremium(), plan, user.getPremiumUntil());
    }
}
