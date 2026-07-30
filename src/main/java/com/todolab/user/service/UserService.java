package com.todolab.user.service;

import com.todolab.auth.dto.RegisterRequest;
import com.todolab.user.domain.User;
import com.todolab.user.dto.UserResponse;
import com.todolab.user.dto.UserTimeZoneRequest;
import com.todolab.user.exception.UserEmailAlreadyExistsException;
import com.todolab.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        User saved = userRepository.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName()
        ));

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateTimeZoneForOwner(User owner, UserTimeZoneRequest request) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        owner.updateTimeZone(request.normalizedTimeZone());
        return UserResponse.from(userRepository.save(owner));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
