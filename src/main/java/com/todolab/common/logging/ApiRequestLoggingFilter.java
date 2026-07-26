package com.todolab.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final ApiLoggingProperties properties;
    private final ApiPayloadSanitizer sanitizer;

    @Autowired
    public ApiRequestLoggingFilter(
            ObjectProvider<ApiLoggingProperties> propertiesProvider,
            ObjectProvider<ApiPayloadSanitizer> sanitizerProvider
    ) {
        this(
                propertiesProvider.getIfAvailable(ApiLoggingProperties::new),
                sanitizerProvider.getIfAvailable(ApiPayloadSanitizer::new)
        );
    }

    ApiRequestLoggingFilter(ApiLoggingProperties properties, ApiPayloadSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.isEnabled()
                || !path.startsWith("/api/")
                || properties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        ContentCachingRequestWrapper requestWrapper = null;
        ContentCachingResponseWrapper responseWrapper = null;
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        if (properties.isPayloadEnabled()) {
            requestWrapper = new ContentCachingRequestWrapper(request, requestCacheLimit());
            responseWrapper = new ContentCachingResponseWrapper(response);
            requestToUse = requestWrapper;
            responseToUse = responseWrapper;
        }

        log.info(
                "API_REQUEST_IN requestId={} method={} path={} query={} remoteIp={} userAgent={} headers={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                sanitizeQuery(request.getQueryString()),
                clientIp(request),
                headerValue(request, "User-Agent"),
                requestHeaders(request)
        );

        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "API_RESPONSE_OUT requestId={} method={} path={} status={} elapsedMs={} requestBody={} responseBody={} headers={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    responseToUse.getStatus(),
                    elapsedMs,
                    requestBody(requestWrapper, request.getRequestURI()),
                    responseBody(responseWrapper, request.getRequestURI()),
                    responseHeaders(responseToUse)
            );
            try {
                if (responseWrapper != null) {
                    responseWrapper.copyBodyToResponse();
                }
            } finally {
                MDC.remove(REQUEST_ID_MDC_KEY);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sanitizer.sanitizeValue(REQUEST_ID_HEADER, requestId, properties.getSensitiveFields(), 128);
    }

    private String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder();
        for (String pair : queryString.split("&")) {
            if (!sanitized.isEmpty()) {
                sanitized.append("&");
            }
            int separator = pair.indexOf('=');
            if (separator < 0) {
                sanitized.append(sanitizer.sanitizeValue(pair, pair, properties.getSensitiveFields(), properties.getMaxPayloadLength()));
                continue;
            }
            String key = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            sanitized.append(key)
                    .append("=")
                    .append(sanitizer.sanitizeValue(key, value, properties.getSensitiveFields(), properties.getMaxPayloadLength()));
        }
        return sanitized.toString();
    }

    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(
                        name,
                        sanitizer.sanitizeValue(name, request.getHeader(name), properties.getSensitiveFields(), properties.getMaxPayloadLength())
                )
        );
        return headers;
    }

    private Map<String, String> responseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name ->
                headers.put(
                        name,
                        sanitizer.sanitizeValue(name, response.getHeader(name), properties.getSensitiveFields(), properties.getMaxPayloadLength())
                )
        );
        return headers;
    }

    private String requestBody(ContentCachingRequestWrapper request, String path) {
        if (request == null) {
            return payloadDisabledReason(null);
        }
        if (isPayloadPathExcluded(path)) {
            return "[PAYLOAD_PATH_EXCLUDED]";
        }
        if (!canLogPayload(request.getContentType())) {
            return payloadDisabledReason(request.getContentType());
        }
        return payload(request.getContentAsByteArray(), request.getCharacterEncoding(), request.getContentType());
    }

    private String responseBody(ContentCachingResponseWrapper response, String path) {
        if (response == null) {
            return payloadDisabledReason(null);
        }
        if (isPayloadPathExcluded(path)) {
            return "[PAYLOAD_PATH_EXCLUDED]";
        }
        if (!canLogPayload(response.getContentType())) {
            return payloadDisabledReason(response.getContentType());
        }
        return payload(response.getContentAsByteArray(), response.getCharacterEncoding(), response.getContentType());
    }

    private boolean canLogPayload(String contentType) {
        if (!properties.isPayloadEnabled()) {
            return false;
        }
        if (contentType == null) {
            return true;
        }

        return contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || contentType.startsWith(MediaType.TEXT_PLAIN_VALUE)
                || contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    private String payloadDisabledReason(String contentType) {
        if (!properties.isPayloadEnabled()) {
            return "[PAYLOAD_DISABLED]";
        }
        return "[UNSUPPORTED_CONTENT_TYPE:" + contentType + "]";
    }

    private String payload(byte[] content, String encoding, String contentType) {
        if (content.length == 0) {
            return "";
        }

        String payload = toString(content, encoding);
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return sanitizer.sanitizeJsonPayload(payload, properties.getSensitiveFields(), properties.getMaxPayloadLength());
        }
        return sanitizer.sanitizeValue(null, payload, properties.getSensitiveFields(), properties.getMaxPayloadLength());
    }

    private String toString(byte[] content, String encoding) {
        try {
            return new String(content, encoding == null ? StandardCharsets.UTF_8.name() : encoding);
        } catch (UnsupportedEncodingException e) {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String headerValue(HttpServletRequest request, String name) {
        return sanitizer.sanitizeValue(name, request.getHeader(name), properties.getSensitiveFields(), properties.getMaxPayloadLength());
    }

    private boolean isPayloadPathExcluded(String path) {
        return properties.getPayloadExcludedPaths().stream().anyMatch(path::startsWith);
    }

    private int requestCacheLimit() {
        int maxPayloadLength = properties.getMaxPayloadLength();
        if (maxPayloadLength <= 0 || maxPayloadLength == Integer.MAX_VALUE) {
            return maxPayloadLength;
        }
        return maxPayloadLength + 1;
    }
}
