package pj.dooit.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("사용자 생성 시 이메일을 정규화하고 기본 권한을 USER로 설정한다")
    void create_normalizesEmailAndDefaultRole() {
        User user = new User("  TEST@Example.COM  ", "  encoded-password  ", "  테스터  ");

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getDisplayName()).isEqualTo("테스터");
        assertThat(user.getAccountType()).isEqualTo(AccountType.REGISTERED);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getTimeZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("게스트 사용자는 이메일과 비밀번호 없이 생성한다")
    void createGuest() {
        User user = User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0));

        assertThat(user.getAccountType()).isEqualTo(AccountType.GUEST);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getGuestExpiresAt()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 9, 0, 0));
        assertThat(user.getLastActiveAt()).isNotNull();
    }

    @Test
    @DisplayName("명시한 권한으로 사용자를 생성한다")
    void create_withRole() {
        User user = new User("admin@example.com", "encoded-password", "관리자", UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("필수값이 비어 있으면 사용자 생성에 실패한다")
    void create_failsWhenRequiredValueBlank() {
        assertThatThrownBy(() -> new User(" ", "encoded-password", "테스터"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email은 필수입니다.");

        assertThatThrownBy(() -> new User("test@example.com", " ", "테스터"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("passwordHash는 필수입니다.");

        assertThatThrownBy(() -> new User("test@example.com", "encoded-password", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName은 필수입니다.");
    }

    @Test
    @DisplayName("사용자 timezone을 변경한다")
    void updateTimeZone_success() {
        User user = new User("test@example.com", "encoded-password", "테스터");

        user.updateTimeZone(" America/New_York ");

        assertThat(user.getTimeZone()).isEqualTo("America/New_York");
    }
}
