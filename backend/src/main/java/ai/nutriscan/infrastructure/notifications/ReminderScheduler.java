package ai.nutriscan.infrastructure.notifications;

import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recordatorios push vía Firebase Cloud Messaging.
 * Activar con nutriscan.notifications.enabled=true (requiere credenciales
 * de Firebase). Los horarios están pensados en hora local del mercado
 * principal (America/Santiago).
 */
@Component
@ConditionalOnProperty(name = "nutriscan.notifications.enabled", havingValue = "true")
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final UserRepository users;

    public ReminderScheduler(UserRepository users) { this.users = users; }

    /** Recordatorio de hidratación a media mañana y media tarde. */
    @Scheduled(cron = "0 0 11,17 * * *", zone = "America/Santiago")
    public void waterReminder() {
        broadcast("💧 ¡Hora de hidratarte!",
                "Un vaso de agua ahora te acerca a tu meta diaria.");
    }

    /** Recordatorio de registrar la cena. */
    @Scheduled(cron = "0 30 20 * * *", zone = "America/Santiago")
    public void dinnerReminder() {
        broadcast("🍽️ ¿Ya cenaste?",
                "Escanea tu cena y mantén tu registro del día completo.");
    }

    private void broadcast(String title, String body) {
        List<User> recipients = users.findByFcmTokenIsNotNull();
        int sent = 0;
        for (User user : recipients) {
            try {
                FirebaseMessaging.getInstance().send(Message.builder()
                        .setToken(user.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build());
                sent++;
            } catch (Exception e) {
                // Token inválido/expirado: se limpia para no reintentar indefinidamente
                log.debug("FCM token inválido para el usuario {}: {}", user.getId(), e.getMessage());
                user.setFcmToken(null);
            }
        }
        log.info("Recordatorio '{}' enviado a {}/{} dispositivos", title, sent, recipients.size());
    }
}
