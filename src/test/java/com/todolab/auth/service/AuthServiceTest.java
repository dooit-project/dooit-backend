package com.todolab.auth.service;

import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.auth.exception.InvalidCredentialsException;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenService jwtTokenService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    @DisplayName("로그인 성공 시 이메일을 정규화하고 access token을 반환한다")
    void login_success() {
        User user = new User("test@example.com", "encoded-password", "테스터");
        LoginRequest request = new LoginRequest(" TEST@Example.COM ", "password123");
        JwtTokenService.AccessToken accessToken = new JwtTokenService.AccessToken(
                "access-token",
                LocalDateTime.of(2026, 6, 30, 1, 0)
        );

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
        given(jwtTokenService.createAccessToken(user)).willReturn(accessToken);

        TokenResponse response = authService.login(request);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresAt()).isEqualTo(accessToken.expiresAt());
        assertThat(response.user().email()).isEqualTo("test@example.com");
        assertThat(response.user().accountType()).isEqualTo(AccountType.REGISTERED);

        then(userRepository).should().findByEmail("test@example.com");
        then(passwordEncoder).should().matches("password123", "encoded-password");
        then(jwtTokenService).should().createAccessToken(user);
    }

    @Test
    @DisplayName("게스트 계정을 생성하고 게스트 access token을 반환한다")
    void createGuest_success() {
        JwtTokenService.AccessToken accessToken = new JwtTokenService.AccessToken(
                "guest-token",
                LocalDateTime.of(2026, 9, 9, 0, 0)
        );

        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> {
                    User guest = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 10L);
                    return guest;
                });
        given(jwtTokenService.guestAccessTokenTtl()).willReturn(java.time.Duration.ofDays(31));
        given(jwtTokenService.createGuestAccessToken(org.mockito.ArgumentMatchers.any(User.class))).willReturn(accessToken);

        TokenResponse response = authService.createGuest();

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("guest-token");
        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.user().accountType()).isEqualTo(AccountType.GUEST);
        assertThat(response.user().email()).isNull();
        assertThat(response.user().displayName()).isNull();
    }

    @Test
    @DisplayName("사용자가 없으면 로그인에 실패한다")
    void login_failsWhenUserMissing() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");
        given(userRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        then(userRepository).should().findByEmail("missing@example.com");
        then(passwordEncoder).shouldHaveNoInteractions();
        then(jwtTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비밀번호가 다르면 로그인에 실패한다")
    void login_failsWhenPasswordMismatch() {
        User user = new User("test@example.com", "encoded-password", "테스터");
        LoginRequest request = new LoginRequest("test@example.com", "wrong-password");

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        then(userRepository).should().findByEmail("test@example.com");
        then(passwordEncoder).should().matches("wrong-password", "encoded-password");
        then(jwtTokenService).shouldHaveNoInteractions();
    }
}
