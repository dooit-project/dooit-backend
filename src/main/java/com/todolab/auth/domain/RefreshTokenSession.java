package com.todolab.auth.domain;

import com.todolab.Constant;
import com.todolab.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "`REFRESH_TOKEN_SESSION`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_REFRESH_TOKEN_HASH", columnNames = "`TOKEN_HASH`")
        },
        indexes = {
                @Index(name = "IDX_REFRESH_TOKEN_USER_ACTIVE", columnList = "`USER_ID`, `REVOKED_AT`, `IDLE_EXPIRES_AT`"),
                @Index(name = "IDX_REFRESH_TOKEN_FAMILY", columnList = "`FAMILY_ID`")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`USER_ID`", nullable = false)
    private User user;

    @Column(name = "`FAMILY_ID`", nullable = false, length = 36)
    private String familyId;

    @Column(name = "`TOKEN_HASH`", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "`IDLE_EXPIRES_AT`", nullable = false)
    private LocalDateTime idleExpiresAt;

    @Column(name = "`ABSOLUTE_EXPIRES_AT`", nullable = false)
    private LocalDateTime absoluteExpiresAt;

    @Column(name = "`LAST_USED_AT`")
    private LocalDateTime lastUsedAt;

    @Column(name = "`REPLACED_AT`")
    private LocalDateTime replacedAt;

    @Column(name = "`REVOKED_AT`")
    private LocalDateTime revokedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    public RefreshTokenSession(
            User user,
            String familyId,
            String tokenHash,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt
    ) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public boolean isExpired(LocalDateTime now) {
        return !idleExpiresAt.isAfter(now) || !absoluteExpiresAt.isAfter(now);
    }

    public boolean isCurrentUsable(LocalDateTime now) {
        return revokedAt == null && replacedAt == null && !isExpired(now);
    }

    public void markReplaced(LocalDateTime now) {
        this.replacedAt = now;
        this.lastUsedAt = now;
    }

    public void revoke(LocalDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
