package com.todolab.calendar.domain;

import com.todolab.Constant;
import com.todolab.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "`CALENDAR_FEED_TOKEN`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_CALENDAR_FEED_TOKEN_HASH", columnNames = "`TOKEN_HASH`")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarFeedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Column(name = "`TOKEN_HASH`", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "`ACTIVE`", nullable = false)
    private boolean active;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`REVOKED_AT`")
    private LocalDateTime revokedAt;

    public CalendarFeedToken(User owner, String tokenHash) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash는 필수입니다.");
        }
        this.owner = owner;
        this.tokenHash = tokenHash;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public void revoke(LocalDateTime revokedAt) {
        this.active = false;
        this.revokedAt = revokedAt == null ? LocalDateTime.now(Constant.ZONE) : revokedAt;
    }
}
