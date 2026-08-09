package com.todolab.auth.service;

import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    UserRepository userRepository;

    @Test
    @DisplayName("JWT subject로 현재 사용자를 조회한다")
    void requireUser_success() {
        CurrentUserService service = new CurrentUserService(userRepository);
        User user = new User("owner@example.com", "encoded-password", "Owner");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        User result = service.requireUser(jwt("1", "REGISTERED"));

        assertThat(result).isSameAs(user);
        then(userRepository).should().findById(1L);
    }

    @Test
    @DisplayName("JWT accountType과 DB accountType이 다르면 인증 예외를 던진다")
    void requireUser_fail_accountTypeMismatch() {
        CurrentUserService service = new CurrentUserService(userRepository);
        User user = new User("owner@example.com", "encoded-password", "Owner");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.requireUser(jwt("1", "GUEST")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("인증 정보가 올바르지 않습니다.");

        then(userRepository).should().findById(1L);
    }

    @Test
    @DisplayName("병합 완료된 게스트 사용자는 기존 token으로 인증할 수 없다")
    void requireUser_fail_mergedGuest() {
        CurrentUserService service = new CurrentUserService(userRepository);
        User guest = User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(guest, "id", 1L);
        User target = new User("target@example.com", "encoded-password", "Target");
        org.springframework.test.util.ReflectionTestUtils.setField(target, "id", 2L);
        guest.markMergedInto(target);
        given(userRepository.findById(1L)).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> service.requireUser(jwt("1", "GUEST")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("인증 정보가 올바르지 않습니다.");

        then(userRepository).should().findById(1L);
    }

    @Test
    @DisplayName("JWT가 없으면 인증 예외를 던진다")
    void requireUser_fail_nullJwt() {
        CurrentUserService service = new CurrentUserService(userRepository);

        assertThatThrownBy(() -> service.requireUser(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("인증 정보가 필요합니다.");

        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("JWT subject가 숫자가 아니면 인증 예외를 던진다")
    void requireUser_fail_invalidSubject() {
        CurrentUserService service = new CurrentUserService(userRepository);

        assertThatThrownBy(() -> service.requireUser(jwt("not-a-number")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("인증 정보가 올바르지 않습니다.");

        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("JWT subject에 해당하는 사용자가 없으면 예외를 던진다")
    void requireUser_fail_notFound() {
        CurrentUserService service = new CurrentUserService(userRepository);
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireUser(jwt("99")))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");

        then(userRepository).should().findById(99L);
    }

    private Jwt jwt(String subject) {
        return jwt(subject, null);
    }

    private Jwt jwt(String subject, String accountType) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject);
        if (accountType != null) {
            builder.claim("accountType", accountType);
        }
        return builder.build();
    }
}
