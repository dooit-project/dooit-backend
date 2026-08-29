package pj.dooit.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.jwt")
public record AuthJwtProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl,
        Duration guestAccessTokenTtl
) {
}
