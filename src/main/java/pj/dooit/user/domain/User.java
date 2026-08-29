package pj.dooit.user.domain;

import pj.dooit.Constant;
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

    @Column(name = "`EMAIL`", length = 255)
    private String email;

    @Column(name = "`PASSWORD_HASH`", length = 255)
    private String passwordHash;

    @Column(name = "`DISPLAY_NAME`", length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "`ACCOUNT_TYPE`", nullable = false, length = 30)
    private AccountType accountType = AccountType.REGISTERED;

    @Column(name = "`MERGED_INTO_USER_ID`")
    private Long mergedIntoUserId;

    @Column(name = "`MERGED_AT`")
    private LocalDateTime mergedAt;

    @Column(name = "`LAST_ACTIVE_AT`")
    private LocalDateTime lastActiveAt;

    @Column(name = "`GUEST_EXPIRES_AT`")
    private LocalDateTime guestExpiresAt;

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
        this.accountType = AccountType.REGISTERED;
    }

    public static User guest(LocalDateTime guestExpiresAt) {
        User user = new User();
        user.role = UserRole.USER;
        user.accountType = AccountType.GUEST;
        user.timeZone = Constant.ZONE_ID;
        user.lastActiveAt = LocalDateTime.now(Constant.ZONE);
        user.guestExpiresAt = guestExpiresAt;
        return user;
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
        this.accountType = AccountType.REGISTERED;
        if (this.timeZone == null) {
            this.timeZone = Constant.ZONE_ID;
        }
    }

    public void promoteGuest(String email, String passwordHash, String displayName) {
        if (this.accountType != AccountType.GUEST || this.mergedIntoUserId != null) {
            throw new IllegalStateException("게스트 계정만 승격할 수 있습니다.");
        }
        update(email, passwordHash, displayName);
        this.mergedAt = null;
        this.mergedIntoUserId = null;
        this.guestExpiresAt = null;
        this.lastActiveAt = LocalDateTime.now(Constant.ZONE);
    }

    public void markMergedInto(User target) {
        if (target == null || target.getId() == null) {
            throw new IllegalArgumentException("target은 영속화된 사용자여야 합니다.");
        }
        if (this.accountType != AccountType.GUEST) {
            throw new IllegalStateException("게스트 계정만 병합할 수 있습니다.");
        }
        this.mergedIntoUserId = target.getId();
        this.mergedAt = LocalDateTime.now(Constant.ZONE);
        this.guestExpiresAt = null;
        this.lastActiveAt = LocalDateTime.now(Constant.ZONE);
    }

    public void refreshGuestExpiration(LocalDateTime guestExpiresAt) {
        if (this.accountType != AccountType.GUEST || this.mergedIntoUserId != null) {
            throw new IllegalStateException("게스트 계정만 갱신할 수 있습니다.");
        }
        this.guestExpiresAt = guestExpiresAt;
        this.lastActiveAt = LocalDateTime.now(Constant.ZONE);
    }

    public void updateTimeZone(String timeZone) {
        String normalizedTimeZone = normalizeRequired(timeZone);
        if (normalizedTimeZone == null) {
            throw new IllegalArgumentException("timeZone은 필수입니다.");
        }
        this.timeZone = normalizedTimeZone;
    }

    public void updatePasswordHash(String passwordHash) {
        String normalizedPasswordHash = normalizeRequired(passwordHash);
        if (normalizedPasswordHash == null) {
            throw new IllegalArgumentException("passwordHash는 필수입니다.");
        }
        this.passwordHash = normalizedPasswordHash;
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
