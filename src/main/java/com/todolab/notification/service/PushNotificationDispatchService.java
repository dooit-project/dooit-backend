package com.todolab.notification.service;

import com.todolab.Constant;
import com.todolab.notification.config.PushNotificationProvider;
import com.todolab.notification.config.PushNotificationProperties;
import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushNotificationSource;
import com.todolab.notification.domain.PushNotificationStatus;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.task.dto.TaskNotificationCandidateResponse;
import com.todolab.task.service.TaskService;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationDispatchService {

    private final PushNotificationProperties properties;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushNotificationHistoryService pushNotificationHistoryService;
    private final TaskService taskService;
    private final ExpoPushClient expoPushClient;

    public int dispatchDueNotifications() {
        if (!properties.enabled()) {
            return 0;
        }
        if (properties.provider() != PushNotificationProvider.EXPO) {
            log.warn("[PUSH] 지원하지 않는 push provider입니다. provider={}", properties.provider());
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(Constant.ZONE);
        LocalDateTime windowEnd = now.plus(properties.lookAheadWindow());
        int sentCount = 0;
        for (User owner : pushDeviceTokenRepository.findDistinctActiveOwners()) {
            sentCount += dispatchForOwner(owner, now, windowEnd);
        }
        return sentCount;
    }

    private int dispatchForOwner(User owner, LocalDateTime now, LocalDateTime windowEnd) {
        List<PushDeviceToken> tokens = pushDeviceTokenRepository
                .findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(owner.getId());
        if (tokens.isEmpty()) {
            return 0;
        }

        LocalDate from = now.toLocalDate();
        LocalDate to = windowEnd.toLocalDate();
        int sentCount = 0;
        for (TaskNotificationCandidateResponse candidate : taskService.getNotificationCandidatesForOwner(from, to, owner)) {
            if (candidate.scheduledAt() == null
                    || candidate.scheduledAt().isBefore(now)
                    || candidate.scheduledAt().isAfter(windowEnd)
                    || pushNotificationHistoryService.shouldSkipServerPush(
                    owner,
                    candidate.taskId(),
                    candidate.recurrenceSeriesId(),
                    candidate.occurrenceDate()
            )) {
                continue;
            }
            sentCount += dispatchCandidate(owner, tokens, candidate);
        }
        return sentCount;
    }

    private int dispatchCandidate(
            User owner,
            List<PushDeviceToken> tokens,
            TaskNotificationCandidateResponse candidate
    ) {
        int sentCount = 0;
        String idempotencyKey = pushNotificationHistoryService.serverIdempotencyKey(
                candidate.taskId(),
                candidate.recurrenceSeriesId(),
                candidate.occurrenceDate()
        );
        for (PushDeviceToken token : tokens) {
            ExpoPushTicket ticket = expoPushClient.send(message(token, candidate));
            record(owner, token, candidate, idempotencyKey, ticket);
            if (ticket.successful()) {
                sentCount++;
            }
        }
        return sentCount;
    }

    private ExpoPushMessage message(PushDeviceToken token, TaskNotificationCandidateResponse candidate) {
        return new ExpoPushMessage(
                token.getDeviceToken(),
                "ToDoLab",
                candidate.task().title(),
                Map.of(
                        "source", "SERVER",
                        "notificationKey", candidate.notificationKey(),
                        "taskId", candidate.taskId()
                )
        );
    }

    private void record(
            User owner,
            PushDeviceToken token,
            TaskNotificationCandidateResponse candidate,
            String idempotencyKey,
            ExpoPushTicket ticket
    ) {
        pushNotificationHistoryService.recordForOwner(new PushNotificationHistoryRecordCommand(
                PushNotificationSource.SERVER,
                PushNotificationProvider.EXPO,
                ticket.successful() ? PushNotificationStatus.SUCCESS : PushNotificationStatus.FAILED,
                token.getId(),
                candidate.taskId(),
                candidate.recurrenceSeriesId(),
                candidate.occurrenceDate(),
                candidate.notificationKey(),
                idempotencyKey,
                ticket.providerMessageId(),
                ticket.errorCode(),
                ticket.errorMessage(),
                null
        ), owner);
    }
}
