package com.todolab.user.domain;

import com.todolab.Constant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "`APP_USER`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_APP_USER_EMAIL", columnNames = "`EMAIL`")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @Column(name = "`EMAIL`", nullable = false, length = 255)
    private String email;

    @Column(name = "`PASSWORD_HASH`", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "`DISPLAY_NAME`", nullable = false, length = 50)
    private String displayName;

    @Column(name = "`TIME_ZONE`", nullable = false, length = 50)
    private String timeZone = Constant.ZONE_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "`ROLE`", nullable = false, length = 30)
    private UserRole role;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public User(String email, String passwordHash, String displayName) {
        this(email, passwordHash, displayName, UserRole.USER);
    }

    public User(String email, String passwordHash, String displayName, UserRole role) {
        update(email, passwordHash, displayName);
        this.role = role == null ? UserRole.USER : role;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void update(String email, String passwordHash, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPasswordHash = normalizeRequired(passwordHash);
        String normalizedDisplayName = normalizeRequired(displayName);

        if (normalizedEmail == null) {
            throw new IllegalArgumentException("email은 필수입니다.");
        }
        if (normalizedPasswordHash == null) {
            throw new IllegalArgumentException("passwordHash는 필수입니다.");
        }
        if (normalizedDisplayName == null) {
            throw new IllegalArgumentException("displayName은 필수입니다.");
        }

        this.email = normalizedEmail;
        this.passwordHash = normalizedPasswordHash;
        this.displayName = normalizedDisplayName;
        if (this.timeZone == null) {
            this.timeZone = Constant.ZONE_ID;
        }
    }

    public void updateTimeZone(String timeZone) {
        String normalizedTimeZone = normalizeRequired(timeZone);
        if (normalizedTimeZone == null) {
            throw new IllegalArgumentException("timeZone은 필수입니다.");
        }
        this.timeZone = normalizedTimeZone;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeRequired(email);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
