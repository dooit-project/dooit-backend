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

import java.time.LocalDateTime;

@Entity
@Table(name = "`GOAL_PLAN`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`GOAL_ID`")
    private Goal goal;

    @Column(name = "`CURRENT_VERSION`", nullable = false)
    private int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private GoalPlanStatus status = GoalPlanStatus.ACTIVE;

    @Column(name = "`ARCHIVED_AT`")
    private LocalDateTime archivedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public GoalPlan(User owner, Goal goal) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
        this.goal = goal;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void advanceVersion(Goal goal) {
        this.currentVersion++;
        this.goal = goal;
    }

    public void activate() {
        if (this.status != GoalPlanStatus.ARCHIVED) {
            this.status = GoalPlanStatus.ACTIVE;
        }
    }

    public void pause() {
        if (this.status != GoalPlanStatus.ARCHIVED) {
            this.status = GoalPlanStatus.PAUSED;
        }
    }

    public void archive() {
        this.status = GoalPlanStatus.ARCHIVED;
        if (this.archivedAt == null) {
            this.archivedAt = LocalDateTime.now(Constant.ZONE);
        }
    }
}
