package com.mediparse.security;

import com.mediparse.common.ForbiddenException;
import com.mediparse.user.User;
import com.mediparse.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ForbiddenException("No authenticated user in context");
        }
        return user;
    }

    public User requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Authenticated user no longer exists"));
    }
}
