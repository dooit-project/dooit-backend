package com.todolab.notification.domain;

import com.todolab.Constant;
import com.todolab.notification.config.PushNotificationProvider;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "`PUSH_NOTIFICATION_HISTORY`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushNotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`OWNER_USER_ID`", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "`PUSH_DEVICE_TOKEN_ID`")
    private PushDeviceToken pushDeviceToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "`SOURCE`", nullable = false, length = 30)
    private PushNotificationSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "`PROVIDER`", nullable = false, length = 30)
    private PushNotificationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "`STATUS`", nullable = false, length = 30)
    private PushNotificationStatus status;

    @Column(name = "`TASK_ID`")
    private Long taskId;

    @Column(name = "`RECURRENCE_SERIES_ID`")
    private Long recurrenceSeriesId;

    @Column(name = "`OCCURRENCE_DATE`")
    private LocalDate occurrenceDate;

    @Column(name = "`NOTIFICATION_KEY`", nullable = false, length = 120)
    private String notificationKey;

    @Column(name = "`IDEMPOTENCY_KEY`", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "`PROVIDER_MESSAGE_ID`", length = 160)
    private String providerMessageId;

    @Column(name = "`ERROR_CODE`", length = 120)
    private String errorCode;

    @Column(name = "`ERROR_MESSAGE`", length = 500)
    private String errorMessage;

    @Column(name = "`ATTEMPTED_AT`", nullable = false)
    private LocalDateTime attemptedAt;

    @Column(name = "`CREATED_AT`", nullable = false)
    private LocalDateTime createdAt;

    public PushNotificationHistory(
            User owner,
            PushDeviceToken pushDeviceToken,
            PushNotificationSource source,
            PushNotificationProvider provider,
            PushNotificationStatus status,
            Long taskId,
            Long recurrenceSeriesId,
            LocalDate occurrenceDate,
            String notificationKey,
            String idempotencyKey,
            String providerMessageId,
            String errorCode,
            String errorMessage,
            LocalDateTime attemptedAt
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
        this.pushDeviceToken = pushDeviceToken;
        this.source = source == null ? PushNotificationSource.SERVER : source;
        this.provider = require(provider, "provider");
        this.status = require(status, "status");
        this.taskId = taskId;
        this.recurrenceSeriesId = recurrenceSeriesId;
        this.occurrenceDate = occurrenceDate;
        this.notificationKey = normalizeRequired(notificationKey, "notificationKey");
        this.idempotencyKey = normalizeRequired(idempotencyKey, "idempotencyKey");
        this.providerMessageId = normalizeOptional(providerMessageId, 160);
        this.errorCode = normalizeOptional(errorCode, 120);
        this.errorMessage = normalizeOptional(errorMessage, 500);
        this.attemptedAt = attemptedAt == null ? LocalDateTime.now(Constant.ZONE) : attemptedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(Constant.ZONE);
    }

    public void assignOwner(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        this.owner = owner;
    }

    private <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value, 160);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
