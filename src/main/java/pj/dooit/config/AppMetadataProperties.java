package pj.dooit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.metadata")
public record AppMetadataProperties(
        String version,
        String commitSha,
        String imageTag
) {

    public AppMetadataProperties {
        version = blankToDefault(version, "local");
        commitSha = blankToDefault(commitSha, "local");
        imageTag = blankToDefault(imageTag, "local");
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
