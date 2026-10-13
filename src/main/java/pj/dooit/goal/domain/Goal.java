package pj.dooit.goal.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`GOAL`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Column(name = "`TITLE`", nullable = false, length = 120)
    private String title;

    @Column(name = "`DESIRED_OUTCOME`", length = 500)
    private String desiredOutcome;

    @Column(name = "`MOTIVATION_TEXT`", length = 500)
    private String motivationText;

    @Column(name = "`TARGET_DATE`")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "`CURRENT_VERSION`", nullable = false)
    private int currentVersion = 1;

    @Column(name = "`LINKED_DDAY_GOAL_ID`")
    private Long linkedDdayGoalId;

    @Column(name = "`ACHIEVED_AT`")
    private LocalDateTime achievedAt;

    @Column(name = "`ARCHIVED_AT`")
    private LocalDateTime archivedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public Goal(
            User owner,
            String title,
            String desiredOutcome,
            String motivationText,
            LocalDate targetDate,
            Long linkedDdayGoalId
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
        updateFields(title, desiredOutcome, motivationText, targetDate, linkedDdayGoalId);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void update(
            String title,
            String desiredOutcome,
            String motivationText,
            LocalDate targetDate,
            Long linkedDdayGoalId
    ) {
        updateFields(title, desiredOutcome, motivationText, targetDate, linkedDdayGoalId);
        this.currentVersion++;
    }

    public void activate() {
        if (this.status != GoalStatus.ARCHIVED) {
            this.status = GoalStatus.ACTIVE;
            this.achievedAt = null;
        }
    }

    public void pause() {
        if (this.status != GoalStatus.ARCHIVED) {
            this.status = GoalStatus.PAUSED;
        }
    }

    public void achieve() {
        if (this.status != GoalStatus.ARCHIVED) {
            this.status = GoalStatus.ACHIEVED;
            if (this.achievedAt == null) {
                this.achievedAt = LocalDateTime.now(Constant.ZONE);
            }
        }
    }

    public void cancelAchievement() {
        if (this.status == GoalStatus.ACHIEVED) {
            this.status = GoalStatus.ACTIVE;
            this.achievedAt = null;
        }
    }

    public void archive() {
        this.status = GoalStatus.ARCHIVED;
        if (this.archivedAt == null) {
            this.archivedAt = LocalDateTime.now(Constant.ZONE);
        }
    }

    private void updateFields(
            String title,
            String desiredOutcome,
            String motivationText,
            LocalDate targetDate,
            Long linkedDdayGoalId
    ) {
        String normalizedTitle = normalizeRequired(title, "title");
        this.title = normalizedTitle;
        this.desiredOutcome = normalizeOptional(desiredOutcome);
        this.motivationText = normalizeOptional(motivationText);
        this.targetDate = targetDate;
        this.linkedDdayGoalId = linkedDdayGoalId;
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
