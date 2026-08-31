package pj.dooit.common.idempotency;

import pj.dooit.Constant;
import pj.dooit.common.api.ErrorCode;
import pj.dooit.config.CorsConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 160;
    private static final List<Pattern> IDEMPOTENT_POST_PATHS = List.of(
            Pattern.compile("^/api/v1/auth/guest$"),
            Pattern.compile("^/api/v1/tasks$"),
            Pattern.compile("^/api/v1/tasks/\\d+/checklist-items$"),
            Pattern.compile("^/api/v1/tasks/quick-capture$"),
            Pattern.compile("^/api/v1/task-templates$"),
            Pattern.compile("^/api/v1/task-templates/\\d+/tasks$"),
            Pattern.compile("^/api/v1/dday-goals$"),
            Pattern.compile("^/api/v1/dday-goals/\\d+/tasks$"),
            Pattern.compile("^/api/v1/workspaces$"),
            Pattern.compile("^/api/v1/workspaces/\\d+/members$"),
            Pattern.compile("^/api/v1/workspaces/\\d+/tasks$"),
            Pattern.compile("^/api/v1/workspaces/\\d+/dday-goals$")
    );

    private final IdempotencyRecordRepository repository;
    private final JwtDecoder jwtDecoder;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public IdempotencyFilter(
            ObjectProvider<IdempotencyRecordRepository> repositoryProvider,
            ObjectProvider<JwtDecoder> jwtDecoderProvider
    ) {
        this.repository = repositoryProvider.getIfAvailable();
        this.jwtDecoder = jwtDecoderProvider.getIfAvailable();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String key = request.getHeader(CorsConfig.IDEMPOTENCY_KEY_HEADER);
        return key == null
                || repository == null
                || key.isBlank()
                || key.length() > MAX_KEY_LENGTH
                || !"POST".equalsIgnoreCase(request.getMethod())
                || IDEMPOTENT_POST_PATHS.stream().noneMatch(pattern -> pattern.matcher(request.getRequestURI()).matches());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String idempotencyKey = request.getHeader(CorsConfig.IDEMPOTENCY_KEY_HEADER).trim();
        String scopeKey = scopeKey(request);
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        String requestHash = sha256(cachedRequest.cachedBody());
        String lockKey = scopeKey + ":" + method + ":" + path + ":" + idempotencyKey;
        Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());

        synchronized (lock) {
            try {
                IdempotencyRecord existing = repository
                        .findByScopeKeyAndHttpMethodAndRequestPathAndIdempotencyKey(
                                scopeKey,
                                method,
                                path,
                                idempotencyKey
                        )
                        .orElse(null);
                if (existing != null) {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        writeConflict(response);
                        return;
                    }
                    replay(existing, response);
                    return;
                }

                ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
                filterChain.doFilter(cachedRequest, responseWrapper);
                if (HttpStatus.valueOf(responseWrapper.getStatus()).is2xxSuccessful()) {
                    repository.save(new IdempotencyRecord(
                            scopeKey,
                            method,
                            path,
                            idempotencyKey,
                            requestHash,
                            responseWrapper.getStatus(),
                            responseWrapper.getContentType(),
                            responseBody(responseWrapper),
                            LocalDateTime.now(Constant.ZONE).plus(RETENTION)
                    ));
                }
                responseWrapper.copyBodyToResponse();
            } finally {
                locks.remove(lockKey);
            }
        }
    }

    private void replay(IdempotencyRecord existing, HttpServletResponse response) throws IOException {
        response.setStatus(existing.getStatusCode());
        response.setHeader(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true");
        String contentType = existing.getContentType() == null ? MediaType.APPLICATION_JSON_VALUE : existing.getContentType();
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(existing.getResponseBody());
    }

    private void writeConflict(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.IDEMPOTENCY_KEY_REUSED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":"fail","data":null,"error":{"code":%d,"message":"%s"},"timestamp":"%s"}"""
                .formatted(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED.getCode(),
                        ErrorCode.IDEMPOTENCY_KEY_REUSED.getMessage(),
                        LocalDateTime.now(Constant.ZONE)
                ));
    }

    private String responseBody(ContentCachingResponseWrapper responseWrapper) {
        return new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String scopeKey(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                if (jwtDecoder == null) {
                    return "UNAUTH:" + clientSignal(request);
                }
                Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()));
                return "USER:" + jwt.getSubject();
            } catch (JwtException ignored) {
                return "UNAUTH:" + clientSignal(request);
            }
        }
        return "UNAUTH:" + clientSignal(request);
    }

    private String clientSignal(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
