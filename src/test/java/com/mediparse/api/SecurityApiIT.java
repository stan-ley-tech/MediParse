package com.mediparse.api;

import com.mediparse.auth.CreateUserRequest;
import com.mediparse.auth.RegisterRequest;
import com.mediparse.auth.UserResponse;
import com.mediparse.support.IntegrationTestSupport;
import com.mediparse.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApiIT extends IntegrationTestSupport {

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/patients", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointWithGarbageTokenReturnsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this-is-not-a-real-jwt");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/patients", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void selfRegistrationAlwaysCreatesAStaffAccount() {
        var request = new RegisterRequest("new-" + UUID.randomUUID() + "@example.com", "Password123!", "New User");

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().role()).isEqualTo(Role.STAFF);
    }

    @Test
    void nonAdminCannotProvisionNewUsers() {
        String staffToken = loginAs("staff-" + UUID.randomUUID() + "@example.com", Role.STAFF);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(staffToken);
        var request = new CreateUserRequest("someone-" + UUID.randomUUID() + "@example.com", "Password123!",
                "Someone", Role.ADMIN);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/admin/users", new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanProvisionAClinicianAccount() {
        String adminToken = loginAs("admin-" + UUID.randomUUID() + "@example.com", Role.ADMIN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        var request = new CreateUserRequest("clinician-" + UUID.randomUUID() + "@example.com", "Password123!",
                "New Clinician", Role.CLINICIAN);

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/v1/admin/users", new HttpEntity<>(request, headers), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().role()).isEqualTo(Role.CLINICIAN);
    }

    @Test
    void auditLogEndpointIsRestrictedToAdmins() {
        String clinicianToken = loginAs("clinician-audit-" + UUID.randomUUID() + "@example.com", Role.CLINICIAN);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(clinicianToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/audit-logs", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
