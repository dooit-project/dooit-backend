package com.todolab.auth.service;

import com.todolab.auth.config.RefreshTokenProperties;
import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.auth.exception.GuestSessionExpiredException;
import com.todolab.auth.exception.InvalidCredentialsException;
import com.todolab.auth.repository.RefreshTokenSessionRepository;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.notification.repository.PushNotificationHistoryRepository;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskTemplateRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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

    @Mock
    TaskRepository taskRepository;

    @Mock
    DdayGoalRepository ddayGoalRepository;

    @Mock
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Mock
    TaskTemplateRepository taskTemplateRepository;

    @Mock
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Mock
    PushNotificationHistoryRepository pushNotificationHistoryRepository;

    @Mock
    RefreshTokenSessionRepository refreshTokenSessionRepository;

    RefreshTokenProperties refreshTokenProperties = new RefreshTokenProperties(
            java.time.Duration.ofDays(30),
            java.time.Duration.ofDays(90)
    );

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenService,
                taskRepository,
                ddayGoalRepository,
                recurrenceSeriesRepository,
                taskTemplateRepository,
                pushDeviceTokenRepository,
                pushNotificationHistoryRepository,
                refreshTokenSessionRepository,
                refreshTokenProperties
        );
    }

    @Test
    @DisplayName("로그인 성공 시 이메일을 정규화하고 access token을 반환한다")
    void login_success() {
        User user = new User("test@example.com", "encoded-password", "테스터");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
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
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshExpiresAt()).isNotNull();
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
    @DisplayName("게스트 token 갱신은 같은 사용자 id의 새 게스트 token을 반환하고 만료 시각을 연장한다")
    void refreshGuest_success() {
        User guest = User.guest(LocalDateTime.of(2026, 9, 9, 0, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 10L);
        JwtTokenService.AccessToken accessToken = new JwtTokenService.AccessToken(
                "refreshed-guest-token",
                LocalDateTime.of(2026, 9, 10, 0, 0)
        );

        given(userRepository.findWithLockById(10L)).willReturn(Optional.of(guest));
        given(jwtTokenService.guestAccessTokenTtl()).willReturn(java.time.Duration.ofDays(31));
        given(jwtTokenService.createGuestAccessToken(guest)).willReturn(accessToken);

        TokenResponse response = authService.refreshGuest(new AuthService.JwtTokenPrincipal(10L, AccountType.GUEST));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("refreshed-guest-token");
        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.user().accountType()).isEqualTo(AccountType.GUEST);
        assertThat(guest.getGuestExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("게스트 token 갱신은 정식 계정 principal이면 401 계약 예외를 던진다")
    void refreshGuest_failsForRegisteredPrincipal() {
        assertThatThrownBy(() -> authService.refreshGuest(
                new AuthService.JwtTokenPrincipal(10L, AccountType.REGISTERED)
        )).isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        then(userRepository).shouldHaveNoInteractions();
        then(jwtTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 게스트 token 갱신은 게스트 세션 만료 예외를 던진다")
    void refreshGuest_failsForExpiredGuest() {
        User guest = User.guest(LocalDateTime.of(2020, 1, 1, 0, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 10L);
        given(userRepository.findWithLockById(10L)).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> authService.refreshGuest(
                new AuthService.JwtTokenPrincipal(10L, AccountType.GUEST)
        )).isInstanceOf(GuestSessionExpiredException.class);

        then(userRepository).should().findWithLockById(10L);
        then(jwtTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("게스트 회원가입 승격은 같은 사용자 id를 유지하고 정식 token을 반환한다")
    void promoteGuest_success() {
        User guest = User.guest(LocalDateTime.of(2026, 9, 9, 0, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 10L);
        RegisterRequest request = new RegisterRequest(" TEST@Example.COM ", "password123", "테스터");
        JwtTokenService.AccessToken accessToken = new JwtTokenService.AccessToken(
                "registered-token",
                LocalDateTime.of(2026, 8, 9, 1, 0)
        );

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(userRepository.findWithLockById(10L)).willReturn(Optional.of(guest));
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(jwtTokenService.createAccessToken(guest)).willReturn(accessToken);

        TokenResponse response = authService.promoteGuest(
                new AuthService.JwtTokenPrincipal(10L, AccountType.GUEST),
                request
        );

        assertThat(response.accessToken()).isEqualTo("registered-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshExpiresAt()).isNotNull();
        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.user().accountType()).isEqualTo(AccountType.REGISTERED);
        assertThat(response.user().email()).isEqualTo("test@example.com");
        assertThat(guest.getGuestExpiresAt()).isNull();
        assertThat(guest.getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("게스트 승격 시 이메일 중복이면 게스트 상태와 데이터가 유지된다")
    void promoteGuest_duplicateEmailKeepsGuest() {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "테스터");
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.promoteGuest(
                new AuthService.JwtTokenPrincipal(10L, AccountType.GUEST),
                request
        )).isInstanceOf(com.todolab.user.exception.UserEmailAlreadyExistsException.class);

        then(userRepository).should().existsByEmail("test@example.com");
        then(userRepository).shouldHaveNoMoreInteractions();
        then(passwordEncoder).shouldHaveNoInteractions();
        then(jwtTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 게스트 회원가입 승격은 게스트 세션 만료 예외를 던진다")
    void promoteGuest_failsForExpiredGuest() {
        User guest = User.guest(LocalDateTime.of(2020, 1, 1, 0, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 10L);
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "테스터");

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(userRepository.findWithLockById(10L)).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> authService.promoteGuest(
                new AuthService.JwtTokenPrincipal(10L, AccountType.GUEST),
                request
        )).isInstanceOf(GuestSessionExpiredException.class);

        then(userRepository).should().existsByEmail("test@example.com");
        then(userRepository).should().findWithLockById(10L);
        then(passwordEncoder).shouldHaveNoInteractions();
        then(jwtTokenService).shouldHaveNoInteractions();
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
