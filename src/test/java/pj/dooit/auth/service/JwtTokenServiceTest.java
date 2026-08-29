package pj.dooit.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import pj.dooit.auth.config.AuthJwtProperties;
import pj.dooit.user.domain.AccountType;
import pj.dooit.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    @DisplayName("사용자 정보를 담은 HS256 access token을 발급한다")
    void createAccessToken() {
        String secret = "test-only-jwt-secret-at-least-32-bytes";
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        AuthJwtProperties properties = new AuthJwtProperties("https://dooit.test", secret, Duration.ofHours(1), Duration.ofDays(31));
        JwtTokenService jwtTokenService = new JwtTokenService(jwtEncoder, properties);
        User user = new User("test@example.com", "encoded-password", "테스터");
        ReflectionTestUtils.setField(user, "id", 7L);

        JwtTokenService.AccessToken accessToken = jwtTokenService.createAccessToken(user);

        assertThat(accessToken.tokenValue()).isNotBlank();
        assertThat(accessToken.expiresAt()).isNotNull();

        Jwt jwt = jwtDecoder.decode(accessToken.tokenValue());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://dooit.test");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("test@example.com");
        assertThat(jwt.getClaimAsString("displayName")).isEqualTo("테스터");
        assertThat(jwt.getClaimAsString("accountType")).isEqualTo("REGISTERED");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("게스트 access token은 accountType claim과 별도 TTL을 사용한다")
    void createGuestAccessToken() {
        String secret = "test-only-jwt-secret-at-least-32-bytes";
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        AuthJwtProperties properties = new AuthJwtProperties("https://dooit.test", secret, Duration.ofHours(1), Duration.ofDays(31));
        JwtTokenService jwtTokenService = new JwtTokenService(jwtEncoder, properties);
        User user = User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0));
        ReflectionTestUtils.setField(user, "id", 8L);

        JwtTokenService.AccessToken accessToken = jwtTokenService.createGuestAccessToken(user);

        Jwt jwt = jwtDecoder.decode(accessToken.tokenValue());
        assertThat(jwt.getSubject()).isEqualTo("8");
        assertThat(jwt.getClaimAsString("accountType")).isEqualTo(AccountType.GUEST.name());
        assertThat(jwt.getClaimAsString("email")).isNull();
        assertThat(jwt.getClaimAsString("displayName")).isNull();
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }
}
