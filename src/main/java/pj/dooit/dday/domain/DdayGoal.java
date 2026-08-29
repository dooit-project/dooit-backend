package pj.dooit.dday.domain;

import pj.dooit.Constant;
import pj.dooit.common.domain.ResourceScope;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.SharedWorkspace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`DDAY_GOAL`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DdayGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @Column(name = "`TITLE`", nullable = false)
    private String title;

    @Column(name = "`TARGET_DATE`", nullable = false)
    private LocalDate targetDate;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`CREATED_BY_USER_ID`")
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "`SCOPE`", nullable = false, length = 30)
    private ResourceScope scope = ResourceScope.PERSONAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`WORKSPACE_ID`")
    private SharedWorkspace workspace;

    public DdayGoal(String title, LocalDate targetDate) {
        this(title, targetDate, null);
    }

    public DdayGoal(String title, LocalDate targetDate, User owner) {
        update(title, targetDate);
        this.owner = owner;
        this.createdBy = owner;
        this.scope = ResourceScope.PERSONAL;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public void update(String title, LocalDate targetDate) {
        String normalizedTitle = normalizeTitle(title);
        if (normalizedTitle == null) {
            throw new IllegalArgumentException("title은 필수입니다.");
        }
        if (targetDate == null) {
            throw new IllegalArgumentException("targetDate는 필수입니다.");
        }

        this.title = normalizedTitle;
        this.targetDate = targetDate;
    }

    public void assignOwner(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
    }

    public void markCreatedBy(User user) {
        if (user == null) {
            throw new IllegalArgumentException("createdBy는 필수입니다.");
        }
        this.createdBy = user;
    }

    public void assignWorkspace(SharedWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace는 필수입니다.");
        }
        this.workspace = workspace;
        this.scope = ResourceScope.WORKSPACE;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String normalized = title.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
