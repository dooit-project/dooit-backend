package com.todolab.task.domain;

import com.todolab.Constant;
import com.todolab.user.domain.User;
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
@Table(name = "`RECURRENCE_SERIES`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurrenceSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "`FREQUENCY`", nullable = false, length = 30)
    private RecurrenceFrequency frequency;

    @Column(name = "`INTERVAL_VALUE`", nullable = false)
    private int interval;

    @Column(name = "`RRULE`", nullable = false, length = 500)
    private String recurrenceRule;

    @Column(name = "`TIME_ZONE`", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "`RECURRENCE_START_AT`", nullable = false)
    private LocalDateTime recurrenceStartAt;

    @Column(name = "`RECURRENCE_UNTIL`")
    private LocalDate recurrenceUntil;

    @Column(name = "`RECURRENCE_COUNT`")
    private Integer recurrenceCount;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "`UPDATED_AT`")
    private LocalDateTime updatedAt;

    public RecurrenceSeries(
            User owner,
            RecurrenceFrequency frequency,
            Integer interval,
            String recurrenceRule,
            String timeZone,
            LocalDateTime recurrenceStartAt,
            LocalDate recurrenceUntil,
            Integer recurrenceCount
    ) {
        update(frequency, interval, recurrenceRule, timeZone, recurrenceStartAt, recurrenceUntil, recurrenceCount);
        this.owner = owner;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(Constant.ZONE);
    }

    public void assignOwner(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
    }

    public void update(
            RecurrenceFrequency frequency,
            Integer interval,
            String recurrenceRule,
            String timeZone,
            LocalDateTime recurrenceStartAt,
            LocalDate recurrenceUntil,
            Integer recurrenceCount
    ) {
        if (frequency == null) {
            throw new IllegalArgumentException("frequency는 필수입니다.");
        }
        if (interval == null || interval < 1) {
            throw new IllegalArgumentException("interval은 1 이상이어야 합니다.");
        }
        String normalizedTimeZone = normalizeRequired(timeZone);
        if (normalizedTimeZone == null) {
            throw new IllegalArgumentException("timeZone은 필수입니다.");
        }
        if (recurrenceStartAt == null) {
            throw new IllegalArgumentException("recurrenceStartAt은 필수입니다.");
        }
        if (recurrenceUntil != null && recurrenceUntil.isBefore(recurrenceStartAt.toLocalDate())) {
            throw new IllegalArgumentException("recurrenceUntil은 recurrenceStartAt 날짜보다 빠를 수 없습니다.");
        }
        if (recurrenceCount != null && recurrenceCount < 1) {
            throw new IllegalArgumentException("recurrenceCount는 1 이상이어야 합니다.");
        }
        String normalizedRule = RecurrenceRuleValidator.validate(
                frequency,
                interval,
                recurrenceRule,
                normalizedTimeZone,
                recurrenceUntil,
                recurrenceCount
        );

        this.frequency = frequency;
        this.interval = interval;
        this.recurrenceRule = normalizedRule;
        this.timeZone = normalizedTimeZone;
        this.recurrenceStartAt = recurrenceStartAt;
        this.recurrenceUntil = recurrenceUntil;
        this.recurrenceCount = recurrenceCount;
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
