package ai.nutriscan.web;

import ai.nutriscan.application.dto.AuthDtos.*;
import ai.nutriscan.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Canje de Firebase ID token por JWT propio")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    @Operation(summary = "Login: canjea un Firebase ID token (Google/Apple/email) por tokens propios")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.firebaseIdToken(), req.fcmToken());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva el access token con un refresh token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }
}
