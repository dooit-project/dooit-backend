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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "`GOAL_PLAN_OCCURRENCE`", uniqueConstraints =
        @UniqueConstraint(name = "UK_GOAL_PLAN_OCCURRENCE_DATE", columnNames = {"PLAN_ID", "SERVICE_DATE"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalPlanOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`PLAN_ID`", nullable = false)
    private GoalPlan plan;

    @Column(name = "`GOAL_ID`")
    private Long goalId;

    @Column(name = "`GOAL_VERSION`")
    private Integer goalVersion;

    @Column(name = "`PLAN_VERSION`", nullable = false)
    private int planVersion;

    @Column(name = "`SERVICE_DATE`", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "`TIME_ZONE`", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "`SNAPSHOT_GOAL_TITLE`", length = 120)
    private String snapshotGoalTitle;

    @Column(name = "`SNAPSHOT_PLAN_NAME`", nullable = false, length = 120)
    private String snapshotPlanName;

    @Column(name = "`SNAPSHOT_TRIGGER_TEXT`", length = 200)
    private String snapshotTriggerText;

    @Column(name = "`SNAPSHOT_STANDARD_ACTION_TEXT`", nullable = false, length = 500)
    private String snapshotStandardActionText;

    @Column(name = "`SNAPSHOT_MINIMUM_ACTION_TEXT`", nullable = false, length = 500)
    private String snapshotMinimumActionText;

    @Column(name = "`SNAPSHOT_NEXT_ACTION`", length = 300)
    private String snapshotNextAction;

    @Column(name = "`SNAPSHOT_NOTIFY_AT_TIME`")
    private LocalTime snapshotNotifyAtTime;

    @Column(name = "`LINKED_TASK_ID`")
    private Long linkedTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private GoalOccurrenceStatus status = GoalOccurrenceStatus.PENDING;

    @Version
    @Column(name = "`ENTITY_VERSION`", nullable = false)
    private long entityVersion;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public GoalPlanOccurrence(GoalPlanVersion version, GoalVersion goalVersion, User owner, LocalDate date) {
        this.plan = version.getPlan();
        this.owner = owner;
        this.serviceDate = date;
        refreshSnapshot(version, goalVersion);
    }

    public void refreshSnapshot(GoalPlanVersion version, GoalVersion goalVersion) {
        this.planVersion = version.getVersion();
        this.goalId = version.getGoal() == null ? null : version.getGoal().getId();
        this.goalVersion = goalVersion == null ? null : goalVersion.getVersion();
        this.snapshotGoalTitle = goalVersion == null ? null : goalVersion.getTitle();
        this.timeZone = version.getScheduleTimeZone();
        this.snapshotPlanName = version.getName();
        this.snapshotTriggerText = version.getTriggerText();
        this.snapshotStandardActionText = version.getStandardActionText();
        this.snapshotMinimumActionText = version.getMinimumActionText();
        this.snapshotNextAction = version.getNextAction();
        this.snapshotNotifyAtTime = version.getNotifyAtTime();
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(Constant.ZONE); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(Constant.ZONE); }
}
