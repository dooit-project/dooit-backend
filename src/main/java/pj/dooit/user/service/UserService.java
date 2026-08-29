package pj.dooit.user.service;

import pj.dooit.auth.dto.RegisterRequest;
import pj.dooit.user.domain.User;
import pj.dooit.user.dto.UserResponse;
import pj.dooit.user.dto.UserTimeZoneRequest;
import pj.dooit.user.exception.UserEmailAlreadyExistsException;
import pj.dooit.user.repository.UserRepository;
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
