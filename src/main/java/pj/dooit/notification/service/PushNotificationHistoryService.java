package pj.dooit.notification.service;

import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.domain.PushNotificationHistory;
import pj.dooit.notification.domain.PushNotificationStatus;
import pj.dooit.notification.dto.PushNotificationHistoryResponse;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.notification.repository.PushNotificationHistoryRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PushNotificationHistoryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> PERMANENT_TOKEN_ERROR_CODES = Set.of(
            "DEVICENOTREGISTERED",
            "INVALIDPUSHTOKEN",
            "INVALIDDEVICETOKEN"
    );

    private final PushNotificationHistoryRepository pushNotificationHistoryRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    @Transactional
    public PushNotificationHistoryResponse recordForOwner(PushNotificationHistoryRecordCommand command, User owner) {
        Long ownerId = ownerId(owner);
        PushDeviceToken pushDeviceToken = command.pushDeviceTokenId() == null
                ? null
                : pushDeviceTokenRepository.findByIdAndOwnerId(command.pushDeviceTokenId(), ownerId).orElse(null);
        if (shouldDeactivateToken(command) && pushDeviceToken != null) {
            pushDeviceToken.deactivate();
        }
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
                idempotencyKey(command),
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
    public boolean shouldSkipServerPush(
            User owner,
            Long taskId,
            Long recurrenceSeriesId,
            LocalDate occurrenceDate
    ) {
        return hasSuccessfulHistory(ownerId(owner), serverIdempotencyKey(taskId, recurrenceSeriesId, occurrenceDate));
    }

    public String serverIdempotencyKey(Long taskId, Long recurrenceSeriesId, LocalDate occurrenceDate) {
        if (recurrenceSeriesId != null && occurrenceDate != null) {
            return "SERVER:%d:%s".formatted(recurrenceSeriesId, occurrenceDate);
        }
        if (taskId != null) {
            return "SERVER:%d".formatted(taskId);
        }
        throw new IllegalArgumentException("taskId 또는 recurrenceSeriesId와 occurrenceDate가 필요합니다.");
    }

    @Transactional(readOnly = true)
    public List<PushNotificationHistoryResponse> getRecentHistoriesForOwner(User owner, Integer limit) {
        return pushNotificationHistoryRepository
                .findByOwnerIdOrderByAttemptedAtDescIdDesc(ownerId(owner), PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(PushNotificationHistoryResponse::from)
                .toList();
    }

    private String idempotencyKey(PushNotificationHistoryRecordCommand command) {
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            return command.idempotencyKey();
        }
        return serverIdempotencyKey(command.taskId(), command.recurrenceSeriesId(), command.occurrenceDate());
    }

    private boolean shouldDeactivateToken(PushNotificationHistoryRecordCommand command) {
        return command.status() == PushNotificationStatus.FAILED && isPermanentTokenError(command.errorCode());
    }

    private boolean isPermanentTokenError(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return false;
        }
        return PERMANENT_TOKEN_ERROR_CODES.contains(errorCode.trim().toUpperCase(Locale.ROOT));
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
