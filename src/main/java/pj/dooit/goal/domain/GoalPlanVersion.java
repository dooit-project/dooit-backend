package pj.dooit.goal.domain;

import pj.dooit.Constant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "`GOAL_PLAN_VERSION`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalPlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`PLAN_ID`", nullable = false)
    private GoalPlan plan;

    @Column(name = "`VERSION`", nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`GOAL_ID`")
    private Goal goal;

    @Column(name = "`NAME`", nullable = false, length = 120)
    private String name;

    @Column(name = "`TRIGGER_TEXT`", length = 200)
    private String triggerText;

    @Column(name = "`SCHEDULE_START_DATE`", nullable = false)
    private LocalDate scheduleStartDate;

    @Column(name = "`SCHEDULE_DAYS`", nullable = false, length = 30)
    private String scheduleDays;

    @Column(name = "`SCHEDULE_TIME_ZONE`", nullable = false, length = 50)
    private String scheduleTimeZone;

    @Column(name = "`NOTIFY_AT_TIME`")
    private LocalTime notifyAtTime;

    @Column(name = "`STANDARD_ACTION_TEXT`", nullable = false, length = 500)
    private String standardActionText;

    @Column(name = "`STANDARD_TARGET_AMOUNT`", length = 100)
    private String standardTargetAmount;

    @Column(name = "`STANDARD_ESTIMATED_MINUTES`")
    private Integer standardEstimatedMinutes;

    @Column(name = "`MINIMUM_ACTION_TEXT`", nullable = false, length = 500)
    private String minimumActionText;

    @Column(name = "`MINIMUM_TARGET_AMOUNT`", length = 100)
    private String minimumTargetAmount;

    @Column(name = "`MINIMUM_ESTIMATED_MINUTES`")
    private Integer minimumEstimatedMinutes;

    @Column(name = "`NEXT_ACTION`", length = 300)
    private String nextAction;

    @Column(name = "`EFFECTIVE_FROM_DATE`", nullable = false)
    private LocalDate effectiveFromDate;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    public GoalPlanVersion(
            GoalPlan plan,
            Goal goal,
            String name,
            String triggerText,
            LocalDate scheduleStartDate,
            List<ScheduleDay> scheduleDays,
            String scheduleTimeZone,
            LocalTime notifyAtTime,
            String standardActionText,
            String standardTargetAmount,
            Integer standardEstimatedMinutes,
            String minimumActionText,
            String minimumTargetAmount,
            Integer minimumEstimatedMinutes,
            String nextAction,
            LocalDate effectiveFromDate
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("plan은 필수입니다.");
        }
        this.plan = plan;
        this.version = plan.getCurrentVersion();
        this.goal = goal;
        this.name = normalizeRequired(name, "name");
        this.triggerText = normalizeOptional(triggerText);
        this.scheduleStartDate = scheduleStartDate;
        this.scheduleDays = serializeDays(scheduleDays);
        this.scheduleTimeZone = scheduleTimeZone;
        this.notifyAtTime = notifyAtTime;
        this.standardActionText = normalizeRequired(standardActionText, "standardActionText");
        this.standardTargetAmount = normalizeOptional(standardTargetAmount);
        this.standardEstimatedMinutes = standardEstimatedMinutes;
        this.minimumActionText = normalizeRequired(minimumActionText, "minimumActionText");
        this.minimumTargetAmount = normalizeOptional(minimumTargetAmount);
        this.minimumEstimatedMinutes = minimumEstimatedMinutes;
        this.nextAction = normalizeOptional(nextAction);
        this.effectiveFromDate = effectiveFromDate;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public List<ScheduleDay> scheduleDays() {
        return Arrays.stream(scheduleDays.split(","))
                .filter(value -> !value.isBlank())
                .map(ScheduleDay::valueOf)
                .toList();
    }

    public boolean isScheduledOn(LocalDate date) {
        return !date.isBefore(scheduleStartDate) && scheduleDays().contains(ScheduleDay.from(date.getDayOfWeek()));
    }

    private String serializeDays(List<ScheduleDay> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("scheduleDays는 필수입니다.");
        }
        return days.stream()
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));
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
