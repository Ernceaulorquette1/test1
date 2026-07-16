package ai.nutriscan.application.service;

import ai.nutriscan.application.dto.AuthDtos.TokenResponse;
import ai.nutriscan.domain.model.User;
import ai.nutriscan.domain.repository.UserRepository;
import ai.nutriscan.infrastructure.firebase.FirebaseTokenVerifier;
import ai.nutriscan.infrastructure.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El cliente se autentica con Firebase (Google/Apple/email) y canjea su
 * ID token por un par de tokens JWT propios del backend.
 */
@Service
public class AuthService {

    private final FirebaseTokenVerifier verifier;
    private final UserRepository users;
    private final JwtService jwt;

    public AuthService(FirebaseTokenVerifier verifier, UserRepository users, JwtService jwt) {
        this.verifier = verifier;
        this.users = users;
        this.jwt = jwt;
    }

    @Transactional
    public TokenResponse login(String firebaseIdToken, String fcmToken) {
        var identity = verifier.verify(firebaseIdToken);
        User user = users.findByFirebaseUid(identity.uid()).orElseGet(() -> {
            User u = new User();
            u.setFirebaseUid(identity.uid());
            u.setEmail(identity.email());
            u.setName(identity.name());
            u.setPhotoUrl(identity.photoUrl());
            return users.save(u);
        });
        if (fcmToken != null && !fcmToken.isBlank()) {
            user.setFcmToken(fcmToken);
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        var userId = jwt.validateRefreshToken(refreshToken);
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        boolean profileComplete = user.getAge() != null && user.getWeightKg() != null
                && user.getHeightCm() != null && user.getGoal() != null;
        return new TokenResponse(
                jwt.createAccessToken(user.getId(), user.isPremium()),
                jwt.createRefreshToken(user.getId()),
                jwt.accessTtlSeconds(),
                profileComplete);
    }
}
