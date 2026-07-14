package ai.nutriscan.infrastructure.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
            "clave-de-prueba-suficientemente-larga-para-hs256!!", 30, 30);

    @Test
    void emiteYValidaAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = jwt.createAccessToken(userId, false);
        assertThat(jwt.validateAccessToken(token)).isEqualTo(userId);
    }

    @Test
    void emiteYValidaRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = jwt.createRefreshToken(userId);
        assertThat(jwt.validateRefreshToken(token)).isEqualTo(userId);
    }

    @Test
    void rechazaRefreshComoAccess() {
        String refresh = jwt.createRefreshToken(UUID.randomUUID());
        assertThatThrownBy(() -> jwt.validateAccessToken(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenManipulado() {
        String[] parts = jwt.createAccessToken(UUID.randomUUID(), false).split("\\.");
        char c = parts[1].charAt(5);
        String tampered = parts[0] + "." + parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A')
                + parts[1].substring(6) + "." + parts[2];
        assertThatThrownBy(() -> jwt.validateAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService("otra-clave-distinta-tambien-larga-para-hs256!!!", 30, 30);
        String token = otro.createAccessToken(UUID.randomUUID(), false);
        assertThatThrownBy(() -> jwt.validateAccessToken(token))
                .isInstanceOf(JwtException.class);
    }
}
