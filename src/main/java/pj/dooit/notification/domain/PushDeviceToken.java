package pj.dooit.notification.domain;

import pj.dooit.Constant;
import pj.dooit.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "`PUSH_DEVICE_TOKEN`",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_PUSH_DEVICE_TOKEN_OWNER_TOKEN", columnNames = {"`OWNER_USER_ID`", "`DEVICE_TOKEN`"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "`PLATFORM`", nullable = false, length = 30)
    private PushPlatform platform;

    @Column(name = "`DEVICE_TOKEN`", nullable = false, length = 512)
    private String deviceToken;

    @Column(name = "`APP_VERSION`", length = 50)
    private String appVersion;

    @Column(name = "`DEVICE_NAME`", length = 100)
    private String deviceName;

    @Column(name = "`ACTIVE`", nullable = false)
    private boolean active;

    @Column(name = "`LAST_REGISTERED_AT`", nullable = false)
    private LocalDateTime lastRegisteredAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public PushDeviceToken(
            User owner,
            PushPlatform platform,
            String deviceToken,
            String appVersion,
            String deviceName
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
        register(platform, deviceToken, appVersion, deviceName);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void register(PushPlatform platform, String deviceToken, String appVersion, String deviceName) {
        if (platform == null) {
            throw new IllegalArgumentException("platform은 필수입니다.");
        }
        String normalizedToken = normalizeRequired(deviceToken);
        if (normalizedToken == null) {
            throw new IllegalArgumentException("deviceToken은 필수입니다.");
        }
        this.platform = platform;
        this.deviceToken = normalizedToken;
        this.appVersion = normalizeOptional(appVersion);
        this.deviceName = normalizeOptional(deviceName);
        this.active = true;
        this.lastRegisteredAt = LocalDateTime.now(Constant.ZONE);
    }

    public void deactivate() {
        this.active = false;
    }

    public void assignOwner(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
