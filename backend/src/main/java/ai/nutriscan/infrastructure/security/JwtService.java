package ai.nutriscan.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Emisión y validación de tokens JWT propios (HS256). */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${nutriscan.jwt.secret}") String secret,
                      @Value("${nutriscan.jwt.access-ttl-minutes}") long accessTtlMinutes,
                      @Value("${nutriscan.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public String createAccessToken(UUID userId, boolean premium) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "access")
                .claim("premium", premium)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(refreshTtl)))
                .signWith(key)
                .compact();
    }

    /** @return el userId del token si es un access token válido. */
    public UUID validateAccessToken(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("type"))) {
            throw new JwtException("El token no es de tipo access");
        }
        return UUID.fromString(claims.getSubject());
    }

    public UUID validateRefreshToken(String token) {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("type"))) {
            throw new JwtException("El token no es de tipo refresh");
        }
        return UUID.fromString(claims.getSubject());
    }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
