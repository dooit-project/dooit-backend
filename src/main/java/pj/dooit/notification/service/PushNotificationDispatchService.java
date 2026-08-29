package pj.dooit.notification.service;

import pj.dooit.Constant;
import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.config.PushNotificationProperties;
import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.domain.PushNotificationSource;
import pj.dooit.notification.domain.PushNotificationStatus;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.task.dto.TaskNotificationCandidateResponse;
import pj.dooit.task.service.TaskService;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.WorkspaceMember;
import pj.dooit.workspace.domain.WorkspaceMemberStatus;
import pj.dooit.workspace.repository.WorkspaceMemberRepository;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;

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
        sentCount += dispatchWorkspaceCandidates(owner, tokens, now, windowEnd);
        return sentCount;
    }

    private int dispatchWorkspaceCandidates(
            User recipient,
            List<PushDeviceToken> tokens,
            LocalDateTime now,
            LocalDateTime windowEnd
    ) {
        LocalDate from = now.toLocalDate();
        LocalDate to = windowEnd.toLocalDate();
        int sentCount = 0;
        for (WorkspaceMember member : workspaceMemberRepository.findByUserIdAndStatusOrderByIdAsc(
                recipient.getId(),
                WorkspaceMemberStatus.ACTIVE
        )) {
            for (TaskNotificationCandidateResponse candidate : taskService.getNotificationCandidatesForWorkspace(
                    from,
                    to,
                    member.getUser(),
                    member.getWorkspace()
            )) {
                if (candidate.scheduledAt() == null
                        || candidate.scheduledAt().isBefore(now)
                        || candidate.scheduledAt().isAfter(windowEnd)
                        || pushNotificationHistoryService.shouldSkipServerPush(
                        recipient,
                        candidate.taskId(),
                        candidate.recurrenceSeriesId(),
                        candidate.occurrenceDate()
                )) {
                    continue;
                }
                sentCount += dispatchCandidate(recipient, tokens, candidate, member.getWorkspace().getId());
            }
        }
        return sentCount;
    }

    private int dispatchCandidate(
            User owner,
            List<PushDeviceToken> tokens,
            TaskNotificationCandidateResponse candidate
    ) {
        return dispatchCandidate(owner, tokens, candidate, null);
    }

    private int dispatchCandidate(
            User owner,
            List<PushDeviceToken> tokens,
            TaskNotificationCandidateResponse candidate,
            Long workspaceId
    ) {
        int sentCount = 0;
        String idempotencyKey = pushNotificationHistoryService.serverIdempotencyKey(
                candidate.taskId(),
                candidate.recurrenceSeriesId(),
                candidate.occurrenceDate()
        );
        for (PushDeviceToken token : tokens) {
            ExpoPushTicket ticket = expoPushClient.send(message(token, candidate, workspaceId));
            record(owner, token, candidate, idempotencyKey, ticket);
            if (ticket.successful()) {
                sentCount++;
            }
        }
        return sentCount;
    }

    private ExpoPushMessage message(PushDeviceToken token, TaskNotificationCandidateResponse candidate, Long workspaceId) {
        Map<String, Object> data = workspaceId == null
                ? Map.of(
                "source", "SERVER",
                "notificationKey", candidate.notificationKey(),
                "taskId", candidate.taskId()
        )
                : Map.of(
                "source", "SERVER",
                "notificationKey", candidate.notificationKey(),
                "taskId", candidate.taskId(),
                "workspaceId", workspaceId
        );
        return new ExpoPushMessage(
                token.getDeviceToken(),
                "Dooit",
                candidate.task().title(),
                data
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
