package com.todolab.notification.service;

import com.todolab.notification.config.PushNotificationProvider;
import com.todolab.notification.config.PushNotificationProperties;
import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushNotificationStatus;
import com.todolab.notification.domain.PushPlatform;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.task.domain.TaskType;
import com.todolab.task.dto.TaskNotificationCandidateResponse;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import com.todolab.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatchServiceTest {

    @Mock
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Mock
    PushNotificationHistoryService pushNotificationHistoryService;

    @Mock
    TaskService taskService;

    @Mock
    ExpoPushClient expoPushClient;

    @Test
    @DisplayName("push가 비활성화되어 있으면 발송 후보를 조회하지 않는다")
    void dispatchDueNotifications_skip_disabled() {
        PushNotificationDispatchService service = service(false, Duration.ofMinutes(10));

        int sentCount = service.dispatchDueNotifications();

        assertThat(sentCount).isZero();
        then(pushDeviceTokenRepository).should(never()).findDistinctActiveOwners();
    }

    @Test
    @DisplayName("look-ahead window 안의 후보를 활성 token으로 발송하고 성공 이력을 기록한다")
    void dispatchDueNotifications_success() {
        PushNotificationDispatchService service = service(true, Duration.ofMinutes(10));
        User owner = owner();
        PushDeviceToken token = token(owner);
        TaskNotificationCandidateResponse candidate = candidate(LocalDateTime.now().plusMinutes(5));
        given(pushDeviceTokenRepository.findDistinctActiveOwners()).willReturn(List.of(owner));
        given(pushDeviceTokenRepository.findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(1L))
                .willReturn(List.of(token));
        given(taskService.getNotificationCandidatesForOwner(any(), any(), any())).willReturn(List.of(candidate));
        given(pushNotificationHistoryService.shouldSkipServerPush(owner, 42L, null, null)).willReturn(false);
        given(pushNotificationHistoryService.serverIdempotencyKey(42L, null, null)).willReturn("SERVER:42");
        given(expoPushClient.send(any())).willReturn(ExpoPushTicket.success("ticket-42"));

        int sentCount = service.dispatchDueNotifications();

        assertThat(sentCount).isEqualTo(1);
        ArgumentCaptor<ExpoPushMessage> messageCaptor = ArgumentCaptor.forClass(ExpoPushMessage.class);
        then(expoPushClient).should(times(1)).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().to()).isEqualTo("ExponentPushToken[token]");
        assertThat(messageCaptor.getValue().body()).isEqualTo("알림 대상");
        ArgumentCaptor<PushNotificationHistoryRecordCommand> commandCaptor =
                ArgumentCaptor.forClass(PushNotificationHistoryRecordCommand.class);
        then(pushNotificationHistoryService).should(times(1)).recordForOwner(commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue().status()).isEqualTo(PushNotificationStatus.SUCCESS);
        assertThat(commandCaptor.getValue().idempotencyKey()).isEqualTo("SERVER:42");
    }

    @Test
    @DisplayName("이미 성공 이력이 있는 후보는 발송하지 않는다")
    void dispatchDueNotifications_skip_successHistory() {
        PushNotificationDispatchService service = service(true, Duration.ofMinutes(10));
        User owner = owner();
        given(pushDeviceTokenRepository.findDistinctActiveOwners()).willReturn(List.of(owner));
        given(pushDeviceTokenRepository.findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(1L))
                .willReturn(List.of(token(owner)));
        given(taskService.getNotificationCandidatesForOwner(any(), any(), any()))
                .willReturn(List.of(candidate(LocalDateTime.now().plusMinutes(5))));
        given(pushNotificationHistoryService.shouldSkipServerPush(owner, 42L, null, null)).willReturn(true);

        int sentCount = service.dispatchDueNotifications();

        assertThat(sentCount).isZero();
        then(expoPushClient).should(never()).send(any());
    }

    private PushNotificationDispatchService service(boolean enabled, Duration lookAheadWindow) {
        return new PushNotificationDispatchService(
                new PushNotificationProperties(
                        enabled,
                        PushNotificationProvider.EXPO,
                        "https://example.com/push",
                        null,
                        Duration.ofMinutes(1),
                        lookAheadWindow
                ),
                pushDeviceTokenRepository,
                pushNotificationHistoryService,
                taskService,
                expoPushClient
        );
    }

    private User owner() {
        User owner = new User("push-owner@example.com", "encoded-password", "Push 사용자");
        ReflectionTestUtils.setField(owner, "id", 1L);
        ReflectionTestUtils.setField(owner, "timeZone", "Asia/Seoul");
        return owner;
    }

    private PushDeviceToken token(User owner) {
        PushDeviceToken token = new PushDeviceToken(
                owner,
                PushPlatform.EXPO,
                "ExponentPushToken[token]",
                "1.0.0",
                "iPhone"
        );
        ReflectionTestUtils.setField(token, "id", 10L);
        return token;
    }

    private TaskNotificationCandidateResponse candidate(LocalDateTime scheduledAt) {
        return new TaskNotificationCandidateResponse(
                "task:42",
                42L,
                scheduledAt,
                null,
                null,
                true,
                TaskResponse.builder()
                        .id(42L)
                        .type(TaskType.TODO)
                        .title("알림 대상")
                        .startAt(scheduledAt)
                        .build()
        );
    }
}
