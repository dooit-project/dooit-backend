package pj.dooit.task.domain;

import pj.dooit.Constant;
import pj.dooit.task.exception.TaskValidationException;
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
import java.time.LocalTime;

@Entity
@Table(name = "`TASK_TEMPLATE`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`")
    private User owner;

    @Column(name = "`TITLE`", nullable = false, length = 30)
    private String title;

    @Column(name = "`DESCRIPTION`", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "`TYPE`", nullable = false, length = 30)
    private TaskType type;

    @Column(name = "`CATEGORY`", length = 30)
    private String category;

    @Column(name = "`ALL_DAY`", nullable = false)
    private boolean allDay;

    @Column(name = "`DEFAULT_START_TIME`")
    private LocalTime defaultStartTime;

    @Column(name = "`DEFAULT_DURATION_MINUTES`")
    private Integer defaultDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "`RECURRENCE_FREQUENCY`", length = 30)
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "`RECURRENCE_INTERVAL`")
    private Integer recurrenceInterval;

    @Column(name = "`RECURRENCE_BY_DAYS`", length = 100)
    private String recurrenceByDays;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public TaskTemplate(
            User owner,
            String title,
            String description,
            TaskType type,
            String category,
            boolean allDay,
            LocalTime defaultStartTime,
            Integer defaultDurationMinutes,
            RecurrenceFrequency recurrenceFrequency,
            Integer recurrenceInterval,
            String recurrenceByDays
    ) {
        assignOwner(owner);
        apply(title, description, type, category, allDay, defaultStartTime, defaultDurationMinutes,
                recurrenceFrequency, recurrenceInterval, recurrenceByDays);
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
            String description,
            TaskType type,
            String category,
            boolean allDay,
            LocalTime defaultStartTime,
            Integer defaultDurationMinutes,
            RecurrenceFrequency recurrenceFrequency,
            Integer recurrenceInterval,
            String recurrenceByDays
    ) {
        apply(title, description, type, category, allDay, defaultStartTime, defaultDurationMinutes,
                recurrenceFrequency, recurrenceInterval, recurrenceByDays);
    }

    public void assignOwner(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
    }

    private void apply(
            String title,
            String description,
            TaskType type,
            String category,
            boolean allDay,
            LocalTime defaultStartTime,
            Integer defaultDurationMinutes,
            RecurrenceFrequency recurrenceFrequency,
            Integer recurrenceInterval,
            String recurrenceByDays
    ) {
        String normalizedTitle = normalizeRequired(title);
        if (normalizedTitle == null) {
            throw new TaskValidationException("템플릿 제목은 필수입니다.");
        }
        if (normalizedTitle.length() > 30) {
            throw new TaskValidationException("템플릿 제목은 30자 이하여야 합니다.");
        }
        if (description != null && description.length() > 300) {
            throw new TaskValidationException("템플릿 설명은 300자 이하여야 합니다.");
        }
        String normalizedCategory = normalizeOptional(category);
        if (normalizedCategory != null && Constant.UNCATEGORIZED.equals(normalizedCategory)) {
            throw new TaskValidationException("'미분류'는 시스템 예약어라서 카테고리명으로 사용할 수 없습니다.");
        }
        if (allDay && defaultStartTime != null) {
            throw new TaskValidationException("종일 템플릿에는 기본 시작 시간을 함께 설정할 수 없습니다.");
        }
        if (defaultDurationMinutes != null && (defaultDurationMinutes < 1 || defaultDurationMinutes > 1440)) {
            throw new TaskValidationException("기본 소요 시간은 1분 이상 1440분 이하여야 합니다.");
        }
        if (recurrenceInterval != null && recurrenceInterval < 1) {
            throw new TaskValidationException("반복 간격은 1 이상이어야 합니다.");
        }

        this.title = normalizedTitle;
        this.description = normalizeOptional(description);
        this.type = type == null ? TaskType.TODO : type;
        this.category = normalizedCategory;
        this.allDay = allDay;
        this.defaultStartTime = defaultStartTime;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.recurrenceFrequency = recurrenceFrequency;
        this.recurrenceInterval = recurrenceInterval == null ? 1 : recurrenceInterval;
        this.recurrenceByDays = normalizeOptional(recurrenceByDays);
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
