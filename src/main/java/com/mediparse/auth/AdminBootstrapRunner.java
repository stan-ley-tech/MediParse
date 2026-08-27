package com.mediparse.auth;

import com.mediparse.user.Role;
import com.mediparse.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator account on startup so the system is never
 * stuck without a way to provision further users. Only runs when no ADMIN
 * exists yet and bootstrap credentials are supplied via environment variables;
 * it is a no-op on every subsequent startup.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final AuthService authService;

    public AdminBootstrapRunner(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean adminExists = userRepository.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN);
        if (adminExists) {
            return;
        }

        String email = System.getenv("ADMIN_BOOTSTRAP_EMAIL");
        String password = System.getenv("ADMIN_BOOTSTRAP_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No ADMIN user exists and ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD are not set. " +
                    "Set them and restart to provision the first administrator.");
            return;
        }

        authService.createUser(email, password, "System Administrator", Role.ADMIN);
        log.info("Bootstrapped initial ADMIN account for {}", email);
    }
}
