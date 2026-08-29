package pj.dooit.auth.security;

import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    UserRepository userRepository;

    @Test
    @DisplayName("이메일로 웹 로그인 사용자를 조회한다")
    void loadUserByUsername() {
        User user = new User("test@example.com", "encoded-password", "테스터");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);

        UserDetails userDetails = service.loadUserByUsername("test@example.com");

        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        then(userRepository).should().findByEmail("test@example.com");
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("이메일에 해당하는 사용자가 없으면 로그인 사용자 조회에 실패한다")
    void loadUserByUsername_notFound() {
        given(userRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);

        then(userRepository).should().findByEmail("missing@example.com");
        then(userRepository).shouldHaveNoMoreInteractions();
    }
}
