package com.mediparse.security;

import com.mediparse.user.Role;
import com.mediparse.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "test-signing-secret-that-is-long-enough-for-hs256-please", 30, 1440);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void generatedAccessTokenRoundTripsUserIdAndRole() {
        User user = userWithId(Role.CLINICIAN);

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).contains(user.getId());
        assertThat(jwtService.extractRole(token)).contains("CLINICIAN");
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        User user = userWithId(Role.STAFF);
        String token = jwtService.generateAccessToken(user);

        JwtService otherService = new JwtService(
                new JwtProperties("a-completely-different-signing-secret-value-here", 30, 1440));

        assertThat(otherService.isValid(token)).isFalse();
        assertThat(otherService.extractUserId(token)).isEmpty();
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.isValid("not-a-real-jwt")).isFalse();
    }

    private User userWithId(Role role) {
        User user = new User("user@example.com", "hashed", "Test User", role);
        user.setId(UUID.randomUUID());
        return user;
    }
}
