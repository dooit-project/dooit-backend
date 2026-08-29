package pj.dooit.auth.domain;

import pj.dooit.Constant;
import pj.dooit.user.domain.User;
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
        name = "`PASSWORD_RESET_TOKEN`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_PASSWORD_RESET_TOKEN_HASH", columnNames = "`TOKEN_HASH`")
        },
        indexes = {
                @Index(name = "IDX_PASSWORD_RESET_EMAIL_CREATED", columnList = "`EMAIL`, `CREATED_AT`"),
                @Index(name = "IDX_PASSWORD_RESET_USER_EXPIRES", columnList = "`USER_ID`, `EXPIRES_AT`")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`USER_ID`", nullable = false)
    private User user;

    @Column(name = "`EMAIL`", nullable = false, length = 255)
    private String email;

    @Column(name = "`TOKEN_HASH`", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "`EXPIRES_AT`", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "`USED_AT`")
    private LocalDateTime usedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    public PasswordResetToken(User user, String email, String tokenHash, LocalDateTime expiresAt) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user는 영속화된 사용자여야 합니다.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email은 필수입니다.");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash는 필수입니다.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt은 필수입니다.");
        }
        this.user = user;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
