package pj.dooit.user.repository;

import pj.dooit.support.RepositoryTestSupport;
import pj.dooit.user.domain.User;
import pj.dooit.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest extends RepositoryTestSupport {

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("사용자를 저장하고 이메일로 조회한다")
    void saveAndFindByEmail() {
        User user = new User("Test@Example.COM", "encoded-password", "테스터");

        User saved = userRepository.save(user);
        flushAndClear();

        User found = userRepository.findByEmail("test@example.com").orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getEmail()).isEqualTo("test@example.com");
        assertThat(found.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(found.getDisplayName()).isEqualTo("테스터");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("이메일은 유니크해야 한다")
    void email_unique() {
        userRepository.save(new User("test@example.com", "encoded-password", "테스터1"));
        flushAndClear();

        assertThatThrownBy(() -> userRepository.save(new User("TEST@example.com", "encoded-password", "테스터2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("이메일 존재 여부를 확인한다")
    void existsByEmail() {
        userRepository.save(new User("test@example.com", "encoded-password", "테스터"));
        flushAndClear();

        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }
}
