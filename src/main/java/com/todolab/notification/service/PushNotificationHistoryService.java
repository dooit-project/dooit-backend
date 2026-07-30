package com.todolab.notification.service;

import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushNotificationHistory;
import com.todolab.notification.domain.PushNotificationStatus;
import com.todolab.notification.dto.PushNotificationHistoryResponse;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.notification.repository.PushNotificationHistoryRepository;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PushNotificationHistoryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    @Transactional
    public PushNotificationHistoryResponse recordForOwner(PushNotificationHistoryRecordCommand command, User owner) {
        Long ownerId = ownerId(owner);
        PushDeviceToken pushDeviceToken = command.pushDeviceTokenId() == null
                ? null
                : pushDeviceTokenRepository.findByIdAndOwnerId(command.pushDeviceTokenId(), ownerId).orElse(null);
        PushNotificationHistory history = new PushNotificationHistory(
                owner,
                pushDeviceToken,
                command.source(),
                command.provider(),
                command.status(),
                command.taskId(),
                command.recurrenceSeriesId(),
                command.occurrenceDate(),
                command.notificationKey(),
                command.idempotencyKey(),
                command.providerMessageId(),
                command.errorCode(),
                command.errorMessage(),
                command.attemptedAt()
        );
        return PushNotificationHistoryResponse.from(pushNotificationHistoryRepository.save(history));
    }

    @Transactional(readOnly = true)
    public boolean hasSuccessfulHistory(Long ownerId, String idempotencyKey) {
        return pushNotificationHistoryRepository.existsByOwnerIdAndIdempotencyKeyAndStatus(
                ownerId,
                idempotencyKey,
                PushNotificationStatus.SUCCESS
        );
    }

    @Transactional(readOnly = true)
    public List<PushNotificationHistoryResponse> getRecentHistoriesForOwner(User owner, Integer limit) {
        return pushNotificationHistoryRepository
                .findByOwnerIdOrderByAttemptedAtDescIdDesc(ownerId(owner), PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(PushNotificationHistoryResponse::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 100 이하이어야 합니다.");
        }
        return limit;
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
