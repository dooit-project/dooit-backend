package pj.dooit.common.idempotency;

import pj.dooit.Constant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "`IDEMPOTENCY_RECORD`",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_IDEMPOTENCY_SCOPE_METHOD_PATH_KEY",
                        columnNames = {"`SCOPE_KEY`", "`HTTP_METHOD`", "`REQUEST_PATH`", "`IDEMPOTENCY_KEY`"}
                )
        },
        indexes = {
                @Index(name = "IDX_IDEMPOTENCY_EXPIRES", columnList = "`EXPIRES_AT`")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @Column(name = "`SCOPE_KEY`", nullable = false, length = 120)
    private String scopeKey;

    @Column(name = "`HTTP_METHOD`", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "`REQUEST_PATH`", nullable = false, length = 300)
    private String requestPath;

    @Column(name = "`IDEMPOTENCY_KEY`", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "`REQUEST_HASH`", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "`STATUS_CODE`", nullable = false)
    private int statusCode;

    @Column(name = "`CONTENT_TYPE`", length = 120)
    private String contentType;

    @Lob
    @Column(name = "`RESPONSE_BODY`", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`EXPIRES_AT`", nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyRecord(
            String scopeKey,
            String httpMethod,
            String requestPath,
            String idempotencyKey,
            String requestHash,
            int statusCode,
            String contentType,
            String responseBody,
            LocalDateTime expiresAt
    ) {
        this.scopeKey = require(scopeKey, "scopeKey");
        this.httpMethod = require(httpMethod, "httpMethod");
        this.requestPath = require(requestPath, "requestPath");
        this.idempotencyKey = require(idempotencyKey, "idempotencyKey");
        this.requestHash = require(requestHash, "requestHash");
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.responseBody = responseBody == null ? "" : responseBody;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
