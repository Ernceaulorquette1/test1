package ai.nutriscan.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Verificación real contra Firebase Authentication. Usa Application Default
 * Credentials (GOOGLE_APPLICATION_CREDENTIALS) — en GKE, Workload Identity.
 */
@Component
public class FirebaseTokenVerifierImpl implements FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenVerifierImpl.class);

    @PostConstruct
    void init() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build());
            } catch (IOException e) {
                // Sin credenciales (entorno local sin Firebase): las llamadas a verify fallarán
                log.warn("Firebase no inicializado — configure GOOGLE_APPLICATION_CREDENTIALS: {}", e.getMessage());
            }
        }
    }

    @Override
    public Identity verify(String idToken) {
        try {
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return new Identity(token.getUid(), token.getEmail(), token.getName(), token.getPicture());
        } catch (Exception e) {
            throw new BadCredentialsException("Firebase ID token inválido", e);
        }
    }
}
