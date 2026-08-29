package pj.dooit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.docs")
public record DocumentationProperties(
        boolean publicEnabled
) {
}
