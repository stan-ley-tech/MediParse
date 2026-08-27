package com.mediparse.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * User provisioning for elevated roles. Self-registration only ever creates
 * STAFF accounts; CLINICIAN and ADMIN accounts must be created deliberately
 * by an existing administrator.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        var user = authService.createUser(request.email(), request.password(), request.fullName(), request.role());
        return UserResponse.from(user);
    }
}
