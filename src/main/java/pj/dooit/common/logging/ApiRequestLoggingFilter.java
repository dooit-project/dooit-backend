package pj.dooit.common.logging;

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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    // These resources contain private planning and practice text, including future AI drafts.
    // This minimum protection must survive replacement of configurable exclusions.
    private static final Set<String> PRIVATE_PLANNING_PATHS = Set.of(
            "/api/v1/goals", "/api/v1/goal-plans", "/api/v1/goal-plan-occurrences",
            "/api/v1/goal-direct-logs", "/api/v1/personal-systems", "/api/v1/personal-system-occurrences");

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
        String path = request.getRequestURI().substring(request.getContextPath().length());
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
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String applicationPath = request.getRequestURI().substring(request.getContextPath().length());
        boolean privatePlanning = isPrivatePlanningPath(applicationPath);

        ContentCachingRequestWrapper requestWrapper = null;
        ContentCachingResponseWrapper responseWrapper = null;
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        if (properties.isPayloadEnabled() && !isPayloadPathExcluded(applicationPath)) {
            requestWrapper = new ContentCachingRequestWrapper(request, requestCacheLimit());
            responseWrapper = new ContentCachingResponseWrapper(response);
            requestToUse = requestWrapper;
            responseToUse = responseWrapper;
        }

        log.info(apiRequestInMessage(
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                privatePlanning && request.getQueryString() != null ? "[QUERY_PATH_EXCLUDED]"
                        : sanitizeQuery(request.getQueryString()),
                clientIp(request),
                headerValue(request, "User-Agent"),
                requestHeaders(request)
        ));

        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(apiResponseOutMessage(
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    responseToUse.getStatus(),
                    elapsedMs,
                    requestBody(requestWrapper, applicationPath),
                    responseBody(responseWrapper, applicationPath),
                    responseHeaders(responseToUse)
            ));
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
        return sanitizer.sanitizeExactValue(REQUEST_ID_HEADER, requestId, properties.getSensitiveHeaders(), 128);
    }

    private String apiRequestInMessage(
            String requestId,
            String method,
            String path,
            String query,
            String remoteIp,
            String userAgent,
            Map<String, String> headers
    ) {
        return """

                ==================================
                  API_REQUEST_IN
                ==================================
                  requestId : %s
                  method    : %s
                  path      : %s
                  query     : %s
                  remoteIp  : %s
                  userAgent : %s
                  headers   : %s
                ==================================""".formatted(
                requestId,
                method,
                path,
                query,
                remoteIp,
                userAgent,
                headers
        );
    }

    private String apiResponseOutMessage(
            String requestId,
            String method,
            String path,
            int status,
            long elapsedMs,
            String requestBody,
            String responseBody,
            Map<String, String> headers
    ) {
        return """

                ==================================
                  API_RESPONSE_OUT
                ==================================
                  requestId    : %s
                  method       : %s
                  path         : %s
                  status       : %d
                  elapsedMs    : %d
                  requestBody  : %s
                  responseBody : %s
                  headers      : %s
                ==================================""".formatted(
                requestId,
                method,
                path,
                status,
                elapsedMs,
                requestBody,
                responseBody,
                headers
        );
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
                sanitized.append(sanitizer.sanitizeExactValue(
                        pair,
                        pair,
                        properties.getSensitiveQueryParameters(),
                        properties.getMaxPayloadLength()
                ));
                continue;
            }
            String key = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            sanitized.append(key)
                    .append("=")
                    .append(sanitizer.sanitizeExactValue(
                            key,
                            value,
                            properties.getSensitiveQueryParameters(),
                            properties.getMaxPayloadLength()
                    ));
        }
        return sanitized.toString();
    }

    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(
                        name,
                        sanitizer.sanitizeExactValue(
                                name,
                                request.getHeader(name),
                                properties.getSensitiveHeaders(),
                                properties.getMaxPayloadLength()
                        )
                )
        );
        return headers;
    }

    private Map<String, String> responseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name ->
                headers.put(
                        name,
                        sanitizer.sanitizeExactValue(
                                name,
                                response.getHeader(name),
                                properties.getSensitiveHeaders(),
                                properties.getMaxPayloadLength()
                        )
                )
        );
        return headers;
    }

    private String requestBody(ContentCachingRequestWrapper request, String path) {
        if (isPayloadPathExcluded(path)) {
            return "[PAYLOAD_PATH_EXCLUDED]";
        }
        if (request == null) {
            return payloadDisabledReason(null);
        }
        if (!canLogPayload(request.getContentType())) {
            return payloadDisabledReason(request.getContentType());
        }
        return payload(request.getContentAsByteArray(), request.getCharacterEncoding(), request.getContentType());
    }

    private String responseBody(ContentCachingResponseWrapper response, String path) {
        if (isPayloadPathExcluded(path)) {
            return "[PAYLOAD_PATH_EXCLUDED]";
        }
        if (response == null) {
            return payloadDisabledReason(null);
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
            return sanitizer.sanitizeJsonPayload(
                    payload,
                    properties.getSensitivePayloadFields(),
                    properties.getMaxPayloadLength()
            );
        }
        return sanitizer.sanitizeValue(null, payload, properties.getSensitivePayloadFields(), properties.getMaxPayloadLength());
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
        return sanitizer.sanitizeExactValue(
                name,
                request.getHeader(name),
                properties.getSensitiveHeaders(),
                properties.getMaxPayloadLength()
        );
    }

    private boolean isPayloadPathExcluded(String path) {
        return isPrivatePlanningPath(path)
                || properties.getPayloadExcludedPaths().stream().anyMatch(path::startsWith);
    }

    private boolean isPrivatePlanningPath(String path) {
        return PRIVATE_PLANNING_PATHS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private int requestCacheLimit() {
        int maxPayloadLength = properties.getMaxPayloadLength();
        if (maxPayloadLength <= 0 || maxPayloadLength == Integer.MAX_VALUE) {
            return maxPayloadLength;
        }
        return maxPayloadLength + 1;
    }
}
