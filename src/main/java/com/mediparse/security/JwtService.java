package com.mediparse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.mediparse.user.User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the HS256-signed JWTs used for stateless authentication.
 * Access tokens carry the user's role as a claim so authorization filters
 * never need to hit the database on every request.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return generateToken(user, properties.accessTokenTtlMinutes());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, properties.refreshTokenTtlMinutes());
    }

    private String generateToken(User user, long ttlMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public Optional<UUID> extractUserId(String token) {
        return parseClaims(token).map(claims -> UUID.fromString(claims.getSubject()));
    }

    public Optional<String> extractRole(String token) {
        return parseClaims(token).map(claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public boolean isValid(String token) {
        return parseClaims(token).isPresent();
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
