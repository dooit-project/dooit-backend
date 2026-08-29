package pj.dooit.task.domain;

import pj.dooit.Constant;
import pj.dooit.common.domain.ResourceScope;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.SharedWorkspace;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "`TASK`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    public static final int STALE_CARRY_OVER_THRESHOLD = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @Column(name = "`TITLE`")
    private String title;

    @Column(name = "`DESCRIPTION`")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "`TYPE`", nullable = false)
    private TaskType type;

    /***
     * 미정 : startAt == null && endAt == null
     * 단일 : startAt != null && endAt == null
     * 기간 : startAT != null && endAt != null
     */
    @Column(name = "`START_AT`")
    private LocalDateTime startAt;

    @Column(name = "`END_AT`")
    private LocalDateTime endAt;

    /***
     * 종일 일정 여부
     *  - true 면 시간 입력 개념이 없으며, startAt/endAt은 00:00으로 정규화되어야 함
     */
    @Column(name = "`ALL_DAY`")
    private boolean allDay;

    @Column(name = "`CATEGORY`")
    private String category;

    @Column(name = "`NOTIFICATION_ENABLED`", nullable = false)
    private boolean notificationEnabled = true;

    @Column(name = "`NOTIFY_AT`")
    private LocalDateTime notifyAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false)
    private TaskStatus status;

    @Column(name = "`TARGET_DATE`")
    private LocalDate targetDate;

    @Column(name = "`TODAY_ORDER`")
    private Integer todayOrder;

    @Column(name = "`COMPLETED_AT`")
    private LocalDateTime completedAt;

    @Column(name = "`CARRY_OVER_COUNT`", nullable = false)
    private int carryOverCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "`DEFER_REASON`")
    private DeferReason deferReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`DDAY_GOAL_ID`")
    private DdayGoal ddayGoal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`RECURRENCE_SERIES_ID`")
    private RecurrenceSeries recurrenceSeries;

    @Column(name = "`OCCURRENCE_DATE`")
    private LocalDate occurrenceDate;

    @Column(name = "`ORIGINAL_OCCURRENCE_DATE`")
    private LocalDate originalOccurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "`RECURRENCE_EXCEPTION`", length = 30)
    private RecurrenceExceptionType recurrenceException;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`CREATED_BY_USER_ID`")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`UPDATED_BY_USER_ID`")
    private User updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "`SCOPE`", nullable = false, length = 30)
    private ResourceScope scope = ResourceScope.PERSONAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`WORKSPACE_ID`")
    private SharedWorkspace workspace;

    @Column(name = "`CREATED_AT`")
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            applyInitialStatus();
        }
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }


    @Builder
    public Task(String title, String description, TaskType type, LocalDateTime startAt, LocalDateTime endAt, boolean allDay, String category,
                Boolean notificationEnabled, LocalDateTime notifyAt,
                TaskStatus status, LocalDate targetDate, Integer todayOrder, LocalDateTime completedAt, Integer carryOverCount,
                DeferReason deferReason, DdayGoal ddayGoal, RecurrenceSeries recurrenceSeries, LocalDate occurrenceDate,
                LocalDate originalOccurrenceDate, RecurrenceExceptionType recurrenceException, User owner) {
        apply(title, description, type, startAt, endAt, allDay, category, notificationEnabled, notifyAt);
        applyStatus(status, targetDate, completedAt);
        this.todayOrder = todayOrder;
        this.carryOverCount = carryOverCount == null ? 0 : Math.max(0, carryOverCount);
        this.deferReason = deferReason;
        this.ddayGoal = ddayGoal;
        this.recurrenceSeries = recurrenceSeries;
        this.occurrenceDate = occurrenceDate;
        this.originalOccurrenceDate = originalOccurrenceDate;
        this.recurrenceException = recurrenceException;
        this.owner = owner;
        this.createdBy = owner;
        this.scope = ResourceScope.PERSONAL;
    }

    public void update(String title, String description, TaskType type, LocalDateTime startAt, LocalDateTime endAt, boolean allDay, String category) {
        update(title, description, type, startAt, endAt, allDay, category, null, null);
    }

    public void update(
            String title,
            String description,
            TaskType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String category,
            Boolean notificationEnabled,
            LocalDateTime notifyAt
    ) {
        apply(title, description, type, startAt, endAt, allDay, category, notificationEnabled, notifyAt);
        if (this.status != TaskStatus.DONE && !isUnscheduled()) {
            applyInitialStatus();
        }
    }

    public boolean isUnscheduled() {
        return startAt == null && endAt == null;
    }

    public boolean isPeriodTask() {
        return startAt != null && endAt != null;
    }

    public boolean isStaleCarryOver() {
        return carryOverCount >= STALE_CARRY_OVER_THRESHOLD;
    }

    public LocalDate getPlannedDate() {
        if (targetDate != null) {
            return targetDate;
        }
        return startAt == null ? null : startAt.toLocalDate();
    }

    public LocalDateTime getNotificationTime() {
        return notifyAt == null ? startAt : notifyAt;
    }

    public void moveToInbox() {
        clearSchedule();
        this.status = TaskStatus.INBOX;
        this.targetDate = null;
        this.todayOrder = null;
        this.completedAt = null;
    }

    public void moveToToday(LocalDate targetDate) {
        validateTargetDate(targetDate);
        moveScheduleTo(targetDate);
        this.status = TaskStatus.TODAY;
        this.targetDate = targetDate;
        this.completedAt = null;
    }

    public void complete(LocalDateTime completedAt) {
        validateCompletedAt(completedAt);
        this.status = TaskStatus.DONE;
        this.completedAt = completedAt;
    }

    public void reopenToday(LocalDate targetDate) {
        validateTargetDate(targetDate);
        moveToToday(targetDate);
    }

    public void carryOverTo(LocalDate nextDate) {
        validateTargetDate(nextDate);
        moveScheduleTo(nextDate);
        this.status = TaskStatus.TODAY;
        this.targetDate = nextDate;
        this.completedAt = null;
        this.carryOverCount++;
    }

    public void assignTodayOrder(int todayOrder) {
        if (todayOrder < 0) {
            throw new IllegalArgumentException("todayOrder는 0 이상이어야 합니다.");
        }
        this.todayOrder = todayOrder;
    }

    public void connectDdayGoal(DdayGoal ddayGoal) {
        if (ddayGoal == null) {
            throw new IllegalArgumentException("ddayGoal은 필수입니다.");
        }
        this.ddayGoal = ddayGoal;
    }

    public void disconnectDdayGoal() {
        this.ddayGoal = null;
    }

    public void connectRecurrenceSeries(RecurrenceSeries recurrenceSeries, LocalDate occurrenceDate) {
        if (recurrenceSeries == null) {
            throw new IllegalArgumentException("recurrenceSeries는 필수입니다.");
        }
        if (occurrenceDate == null) {
            throw new IllegalArgumentException("occurrenceDate는 필수입니다.");
        }
        this.recurrenceSeries = recurrenceSeries;
        this.occurrenceDate = occurrenceDate;
        this.originalOccurrenceDate = occurrenceDate;
        this.recurrenceException = null;
    }

    public void markRecurrenceException(RecurrenceExceptionType recurrenceException, LocalDate originalOccurrenceDate) {
        if (this.recurrenceSeries == null) {
            throw new IllegalArgumentException("반복 series에 연결된 Task만 예외로 표시할 수 있습니다.");
        }
        if (recurrenceException == null) {
            throw new IllegalArgumentException("recurrenceException은 필수입니다.");
        }
        if (originalOccurrenceDate == null) {
            throw new IllegalArgumentException("originalOccurrenceDate는 필수입니다.");
        }
        this.recurrenceException = recurrenceException;
        this.originalOccurrenceDate = originalOccurrenceDate;
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

    public void markUpdatedBy(User user) {
        if (user == null) {
            throw new IllegalArgumentException("updatedBy는 필수입니다.");
        }
        this.updatedBy = user;
    }

    public void assignWorkspace(SharedWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace는 필수입니다.");
        }
        this.workspace = workspace;
        this.scope = ResourceScope.WORKSPACE;
    }

    public void setDeferReason(DeferReason deferReason) {
        if (deferReason == null) {
            throw new IllegalArgumentException("deferReason은 필수입니다.");
        }
        this.deferReason = deferReason;
    }

    public void clearDeferReason() {
        this.deferReason = null;
    }

    private void apply(
            String title,
            String description,
            TaskType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String category,
            Boolean notificationEnabled,
            LocalDateTime notifyAt
    ) {
        validateSchedule(startAt, endAt, allDay);
        validateNotification(startAt, notificationEnabled, notifyAt);

        String normalizedCategory = normalizeCategory(category);
        validateCategory(normalizedCategory);

        this.title = title;
        this.description = description;
        this.type = normalizeType(type);
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.category = normalizedCategory;
        this.notificationEnabled = notificationEnabled == null || notificationEnabled;
        this.notifyAt = notifyAt;

        if (this.status == null) {
            applyInitialStatus();
        }
    }

    private TaskType normalizeType(TaskType type) {
        return type == null ? TaskType.defaultType() : type;
    }

    private void clearSchedule() {
        this.startAt = null;
        this.endAt = null;
        this.allDay = false;
        this.notifyAt = null;
        if (this.type == TaskType.SCHEDULE) {
            this.type = TaskType.TODO;
        }
    }

    private void applyAutoTodaySchedule(LocalDate targetDate) {
        this.startAt = targetDate.atStartOfDay();
        this.endAt = targetDate.plusDays(1).atStartOfDay();
        this.allDay = true;
    }

    private void moveScheduleTo(LocalDate targetDate) {
        if (isUnscheduled()) {
            applyAutoTodaySchedule(targetDate);
            return;
        }

        long daysToMove = ChronoUnit.DAYS.between(this.startAt.toLocalDate(), targetDate);
        this.startAt = this.startAt.plusDays(daysToMove);
        if (this.endAt != null) {
            this.endAt = this.endAt.plusDays(daysToMove);
        }
        if (this.notifyAt != null) {
            this.notifyAt = this.notifyAt.plusDays(daysToMove);
        }
    }

    private void validateTargetDate(LocalDate targetDate) {
        if (targetDate == null) {
            throw new IllegalArgumentException("targetDate는 필수입니다.");
        }
    }

    private void validateCompletedAt(LocalDateTime completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt은 필수입니다.");
        }
    }

    private void applyStatus(TaskStatus status, LocalDate targetDate, LocalDateTime completedAt) {
        if (status == null) {
            applyInitialStatus();
            return;
        }

        this.status = status;
        this.targetDate = targetDate;
        this.completedAt = completedAt;
    }

    private void applyInitialStatus() {
        if (isUnscheduled()) {
            this.status = TaskStatus.INBOX;
            this.targetDate = null;
            this.todayOrder = null;
            this.completedAt = null;
            return;
        }

        this.status = TaskStatus.TODAY;
        this.targetDate = startAt.toLocalDate();
        this.todayOrder = null;
        this.completedAt = null;
    }

    private void validateSchedule(LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        validateEndAtWithoutStartAt(startAt, endAt);
        validateUnscheduledAllDay(startAt, endAt, allDay);
        validateSingleSchedule(startAt, endAt, allDay);
        validatePeriodSchedule(startAt, endAt, allDay);
    }

    private void validateNotification(LocalDateTime startAt, Boolean notificationEnabled, LocalDateTime notifyAt) {
        boolean enabled = notificationEnabled == null || notificationEnabled;
        if (notifyAt == null) {
            return;
        }
        if (!enabled) {
            throw new IllegalArgumentException("알림이 비활성화된 Task에는 notifyAt을 설정할 수 없습니다.");
        }
        if (startAt == null) {
            throw new IllegalArgumentException("notifyAt은 시작 일시가 있는 Task에만 설정할 수 있습니다.");
        }
    }

    private void validateEndAtWithoutStartAt(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null && endAt != null) {
            throw new IllegalArgumentException("endAt이 존재하면 startAt은 필수입니다.");
        }
    }

    private void validateUnscheduledAllDay(LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        if (startAt == null && endAt == null && allDay) {
            throw new IllegalArgumentException("미정 일정에는 allDay 를 설정할 수 없습니다.");
        }
    }

    private void validateSingleSchedule(LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        if (startAt == null || endAt != null) {
            return;
        }

        if (allDay && !isMidnight(startAt)) {
            throw new IllegalArgumentException("allDay 일정의 startAt은 00:00 이어야 합니다.");
        }
    }

    private void validatePeriodSchedule(LocalDateTime startAt, LocalDateTime endAt, boolean allDay) {
        if (startAt == null || endAt == null) {
            return;
        }

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt은 startAt 이후여야 합니다.");
        }

        if (allDay && (!isMidnight(startAt) || !isMidnight(endAt))) {
            throw new IllegalArgumentException("allDay 일정의 startAt/endAt은 00:00 이어야 합니다.");
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return null;
        }

        String normalized = category.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateCategory(String category) {
        if (category == null) {
            return;
        }

        if (Constant.UNCATEGORIZED.equals(category)) {
            throw new IllegalArgumentException("'미분류'는 카테고리명으로 사용할 수 없습니다.");
        }
    }

    private boolean isMidnight(LocalDateTime dateTime) {
        return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT);
    }

}
