package com.mediparse.auth;

import com.mediparse.common.ConflictException;
import com.mediparse.security.JwtProperties;
import com.mediparse.security.JwtService;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import com.mediparse.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtService jwtService,
                        JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public User register(RegisterRequest request) {
        // Public self-registration always lands as STAFF; elevated roles are
        // granted deliberately through the admin user-management endpoint.
        return createUser(request.email(), request.password(), request.fullName(), Role.STAFF);
    }

    @Transactional
    public User createUser(String email, String rawPassword, String fullName, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), fullName, role);
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = (User) authentication.getPrincipal();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        long expiresInSeconds = jwtProperties.accessTokenTtlMinutes() * 60;

        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
