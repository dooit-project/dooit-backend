package com.todolab.common.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.api-logging")
public class ApiLoggingProperties {

    private boolean enabled = true;
    private boolean payloadEnabled = false;
    private int maxPayloadLength = 4096;
    private Set<String> sensitiveFields = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "password",
            "token",
            "accessToken",
            "access_token",
            "refreshToken",
            "refresh_token",
            "secret",
            "jwt"
    );
    private Set<String> excludedPaths = Set.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/scalar.html"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPayloadEnabled() {
        return payloadEnabled;
    }

    public void setPayloadEnabled(boolean payloadEnabled) {
        this.payloadEnabled = payloadEnabled;
    }

    public int getMaxPayloadLength() {
        return maxPayloadLength;
    }

    public void setMaxPayloadLength(int maxPayloadLength) {
        this.maxPayloadLength = maxPayloadLength;
    }

    public Set<String> getSensitiveFields() {
        return sensitiveFields;
    }

    public void setSensitiveFields(Set<String> sensitiveFields) {
        this.sensitiveFields = sensitiveFields;
    }

    public Set<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(Set<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
