package com.todolab.common.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.api-logging")
public class ApiLoggingProperties {

    private boolean enabled = true;
    private boolean payloadEnabled = false;
    private int maxPayloadLength = 4096;
    private Set<String> sensitiveHeaders = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "proxy-authorization",
            "x-api-key"
    );
    private Set<String> sensitiveQueryParameters = Set.of(
            "q",
            "query",
            "keyword",
            "search",
            "email",
            "name",
            "password",
            "token",
            "accessToken",
            "access_token",
            "refreshToken",
            "refresh_token",
            "secret",
            "jwt"
    );
    private Set<String> sensitivePayloadFields = Set.of(
            "email",
            "name",
            "password",
            "token",
            "accessToken",
            "access_token",
            "refreshToken",
            "refresh_token",
            "secret",
            "jwt",
            "title",
            "category",
            "description",
            "content",
            "memo",
            "note",
            "query",
            "keyword",
            "subject",
            "body",
            "to",
            "from"
    );
    private Set<String> excludedPaths = Set.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/scalar.html"
    );
    private Set<String> payloadExcludedPaths = Set.of(
            "/api/auth",
            "/api/v1/auth"
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

    public Set<String> getSensitiveHeaders() {
        return sensitiveHeaders;
    }

    public void setSensitiveHeaders(Set<String> sensitiveHeaders) {
        this.sensitiveHeaders = sensitiveHeaders;
    }

    public Set<String> getSensitiveQueryParameters() {
        return sensitiveQueryParameters;
    }

    public void setSensitiveQueryParameters(Set<String> sensitiveQueryParameters) {
        this.sensitiveQueryParameters = sensitiveQueryParameters;
    }

    public Set<String> getSensitivePayloadFields() {
        return sensitivePayloadFields;
    }

    public void setSensitivePayloadFields(Set<String> sensitivePayloadFields) {
        this.sensitivePayloadFields = sensitivePayloadFields;
    }

    public Set<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(Set<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public Set<String> getPayloadExcludedPaths() {
        return payloadExcludedPaths;
    }

    public void setPayloadExcludedPaths(Set<String> payloadExcludedPaths) {
        this.payloadExcludedPaths = payloadExcludedPaths;
    }
}
