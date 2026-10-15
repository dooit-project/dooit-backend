package pj.dooit.personalsystem.domain;

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
@Table(name = "`PERSONAL_SYSTEM`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @Column(name = "`CURRENT_VERSION`", nullable = false)
    private int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private PersonalSystemStatus status = PersonalSystemStatus.ACTIVE;

    @Column(name = "`LINKED_DDAY_GOAL_ID`")
    private Long linkedDdayGoalId;

    @Column(name = "`MIGRATED_GOAL_ID`", unique = true)
    private Long migratedGoalId;

    @Column(name = "`MIGRATED_PLAN_ID`", unique = true)
    private Long migratedPlanId;

    @Column(name = "`ARCHIVED_AT`")
    private LocalDateTime archivedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public PersonalSystem(User owner, Long linkedDdayGoalId) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
        this.linkedDdayGoalId = linkedDdayGoalId;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void markMigrated(Long goalId, Long planId) {
        this.migratedGoalId = goalId;
        this.migratedPlanId = planId;
        archive();
    }

    public void advanceVersion(Long linkedDdayGoalId) {
        this.currentVersion++;
        this.linkedDdayGoalId = linkedDdayGoalId;
    }

    public void pause() {
        if (this.status != PersonalSystemStatus.ARCHIVED) {
            this.status = PersonalSystemStatus.PAUSED;
        }
    }

    public void resume() {
        if (this.status != PersonalSystemStatus.ARCHIVED) {
            this.status = PersonalSystemStatus.ACTIVE;
        }
    }

    public void archive() {
        this.status = PersonalSystemStatus.ARCHIVED;
        if (this.archivedAt == null) {
            this.archivedAt = LocalDateTime.now(Constant.ZONE);
        }
    }
}
