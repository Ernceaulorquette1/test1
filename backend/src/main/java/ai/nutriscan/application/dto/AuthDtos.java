package ai.nutriscan.application.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}

    /** El cliente se autentica con Firebase y canjea el ID token por un JWT propio. */
    public record LoginRequest(@NotBlank String firebaseIdToken, String fcmToken) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                boolean profileComplete) {}
}
