package pj.dooit.workspace.domain;

import pj.dooit.Constant;
import pj.dooit.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "`SHARED_WORKSPACE`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedWorkspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @Column(name = "`NAME`", nullable = false, length = 50)
    private String name;

    @Column(name = "`DESCRIPTION`", length = 300)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`CREATED_BY_USER_ID`", nullable = false)
    private User createdBy;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public SharedWorkspace(String name, String description, User createdBy) {
        update(name, description);
        if (createdBy == null) {
            throw new IllegalArgumentException("createdBy는 필수입니다.");
        }
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void update(String name, String description) {
        String normalizedName = normalizeRequired(name);
        if (normalizedName == null) {
            throw new IllegalArgumentException("workspace 이름은 필수입니다.");
        }
        if (normalizedName.length() > 50) {
            throw new IllegalArgumentException("workspace 이름은 50자 이하여야 합니다.");
        }
        if (description != null && description.length() > 300) {
            throw new IllegalArgumentException("workspace 설명은 300자 이하여야 합니다.");
        }
        this.name = normalizedName;
        this.description = normalizeOptional(description);
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
