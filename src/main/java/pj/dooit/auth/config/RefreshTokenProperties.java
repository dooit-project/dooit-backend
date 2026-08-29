package pj.dooit.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.refresh-token")
public record RefreshTokenProperties(
        Duration idleTtl,
        Duration absoluteTtl
) {

    public RefreshTokenProperties {
        idleTtl = idleTtl == null ? Duration.ofDays(30) : idleTtl;
        absoluteTtl = absoluteTtl == null ? Duration.ofDays(90) : absoluteTtl;
    }
}
