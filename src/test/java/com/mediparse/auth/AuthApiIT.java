package com.mediparse.auth;

import com.mediparse.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiIT extends IntegrationTestSupport {

    @Test
    void registerThenLoginReturnsAWorkingAccessToken() {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Login Test"), UserResponse.class);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        String email = "wrongpass-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Wrong Pass Test"), UserResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "not-the-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registeringTheSameEmailTwiceIsRejected() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        var request = new RegisterRequest(email, "Password123!", "Duplicate Test");
        restTemplate.postForEntity("/api/v1/auth/register", request, UserResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
