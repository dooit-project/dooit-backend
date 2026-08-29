package pj.dooit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    SecurityConfig securityConfig = new SecurityConfig(null, null, new DocumentationProperties(true));

    @Test
    @DisplayName("BCrypt PasswordEncoder를 제공한다")
    void passwordEncoder() {
        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();

        String encoded = passwordEncoder.encode("password");

        assertThat(encoded).isNotEqualTo("password");
        assertThat(passwordEncoder.matches("password", encoded)).isTrue();
    }

    @Test
    @DisplayName("API 문서 endpoint는 별도 공개 제어 경로로 관리한다")
    void documentationMatchersArePublic() {
        assertThat(SecurityConfig.DOCUMENTATION_MATCHERS)
                .contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/scalar.html");
        assertThat(SecurityConfig.NON_API_PUBLIC_MATCHERS)
                .doesNotContain(SecurityConfig.DOCUMENTATION_MATCHERS);
    }

    @Test
    @DisplayName("actuator health endpoint는 문서 공개 설정과 별도로 공개한다")
    void actuatorHealthMatchersArePublic() {
        assertThat(SecurityConfig.ACTUATOR_HEALTH_MATCHERS)
                .contains("/actuator/health", "/actuator/health/**");
        assertThat(SecurityConfig.NON_API_PUBLIC_MATCHERS)
                .contains(SecurityConfig.ACTUATOR_HEALTH_MATCHERS);
    }
}
