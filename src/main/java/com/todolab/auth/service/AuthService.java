package com.todolab.auth.service;

import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.auth.exception.InvalidCredentialsException;
import com.todolab.Constant;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.dto.UserResponse;
import com.todolab.user.exception.UserEmailAlreadyExistsException;
import com.todolab.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(user);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public TokenResponse createGuest() {
        User guest = userRepository.save(User.guest(
                LocalDateTime.now(Constant.ZONE).plus(jwtTokenService.guestAccessTokenTtl())
        ));
        JwtTokenService.AccessToken accessToken = jwtTokenService.createGuestAccessToken(guest);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(guest)
        );
    }

    @Transactional
    public TokenResponse promoteGuest(JwtTokenPrincipal guestPrincipal, RegisterRequest request) {
        if (guestPrincipal == null || guestPrincipal.accountType() != AccountType.GUEST) {
            throw new InvalidCredentialsException();
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserEmailAlreadyExistsException(email);
        }

        User guest = userRepository.findWithLockById(guestPrincipal.userId())
                .orElseThrow(InvalidCredentialsException::new);
        if (guest.getAccountType() != AccountType.GUEST) {
            throw new InvalidCredentialsException();
        }

        guest.promoteGuest(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName()
        );

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(guest);
        return new TokenResponse(
                "Bearer",
                accessToken.tokenValue(),
                accessToken.expiresAt(),
                UserResponse.from(guest)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record JwtTokenPrincipal(Long userId, AccountType accountType) {
    }
}
