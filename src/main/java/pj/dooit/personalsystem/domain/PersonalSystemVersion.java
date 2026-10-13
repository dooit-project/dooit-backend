package pj.dooit.personalsystem.domain;

import pj.dooit.Constant;
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
@Table(name = "`PERSONAL_SYSTEM_VERSION`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalSystemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`SYSTEM_ID`", nullable = false)
    private PersonalSystem system;

    @Column(name = "`VERSION`", nullable = false)
    private int version;

    @Column(name = "`DIRECTION_TEXT`", nullable = false, length = 500)
    private String directionText;

    @Column(name = "`MOTIVATION_TEXT`", length = 500)
    private String motivationText;

    @Column(name = "`TRIGGER_TEXT`", nullable = false, length = 200)
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

    public PersonalSystemVersion(
            PersonalSystem system,
            int version,
            String directionText,
            String motivationText,
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
        this.system = system;
        this.version = version;
        this.directionText = directionText;
        this.motivationText = motivationText;
        this.triggerText = triggerText;
        this.scheduleStartDate = scheduleStartDate;
        this.scheduleDays = serializeDays(scheduleDays);
        this.scheduleTimeZone = scheduleTimeZone;
        this.notifyAtTime = notifyAtTime;
        this.standardActionText = standardActionText;
        this.standardTargetAmount = standardTargetAmount;
        this.standardEstimatedMinutes = standardEstimatedMinutes;
        this.minimumActionText = minimumActionText;
        this.minimumTargetAmount = minimumTargetAmount;
        this.minimumEstimatedMinutes = minimumEstimatedMinutes;
        this.nextAction = nextAction;
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

    public boolean isScheduledOn(LocalDate serviceDate) {
        if (serviceDate == null || serviceDate.isBefore(scheduleStartDate) || serviceDate.isBefore(effectiveFromDate)) {
            return false;
        }
        ScheduleDay day = ScheduleDay.from(serviceDate.getDayOfWeek());
        return scheduleDays().contains(day);
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
}
