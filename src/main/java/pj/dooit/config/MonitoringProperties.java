package pj.dooit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(
        boolean enabled,
        String username,
        String password,
        String passwordFile
) {
}
