package com.todolab.notification.service;

import com.todolab.notification.config.PushNotificationProvider;
import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.domain.PushNotificationSource;
import com.todolab.notification.domain.PushNotificationStatus;
import com.todolab.notification.domain.PushPlatform;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.notification.repository.PushNotificationHistoryRepository;
import com.todolab.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PushNotificationHistoryServiceTest {

    @Mock
    PushNotificationHistoryRepository pushNotificationHistoryRepository;

    @Mock
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Test
    @DisplayName("서버 push idempotency key는 단건 Task와 반복 occurrence 기준으로 생성한다")
    void serverIdempotencyKey_success() {
        PushNotificationHistoryService service = service();

        assertThat(service.serverIdempotencyKey(42L, null, null))
                .isEqualTo("SERVER:42");
        assertThat(service.serverIdempotencyKey(42L, 7L, LocalDate.of(2026, 8, 23)))
                .isEqualTo("SERVER:7:2026-08-23");
        assertThatThrownBy(() -> service.serverIdempotencyKey(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId");
    }

    @Test
    @DisplayName("서버 push 중복 발송 여부는 owner와 성공 이력 idempotency key로 판단한다")
    void shouldSkipServerPush_success() {
        PushNotificationHistoryService service = service();
        User owner = new User("push-idempotency@example.com", "encoded-password", "Push 사용자");
        ReflectionTestUtils.setField(owner, "id", 10L);
        given(pushNotificationHistoryRepository.existsByOwnerIdAndIdempotencyKeyAndStatus(
                10L,
                "SERVER:7:2026-08-23",
                PushNotificationStatus.SUCCESS
        )).willReturn(true);

        boolean result = service.shouldSkipServerPush(owner, 42L, 7L, LocalDate.of(2026, 8, 23));

        assertThat(result).isTrue();
        then(pushNotificationHistoryRepository).should(times(1)).existsByOwnerIdAndIdempotencyKeyAndStatus(
                10L,
                "SERVER:7:2026-08-23",
                PushNotificationStatus.SUCCESS
        );
    }

    @Test
    @DisplayName("서버 push 이력 기록은 idempotency key가 비어 있으면 기준 필드로 보완한다")
    void recordForOwner_success_generatesMissingIdempotencyKey() {
        PushNotificationHistoryService service = service();
        User owner = new User("push-record@example.com", "encoded-password", "Push 기록 사용자");
        ReflectionTestUtils.setField(owner, "id", 10L);
        given(pushNotificationHistoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.recordForOwner(new PushNotificationHistoryRecordCommand(
                PushNotificationSource.SERVER,
                PushNotificationProvider.EXPO,
                PushNotificationStatus.SUCCESS,
                null,
                42L,
                null,
                null,
                "task:42",
                null,
                "ticket-42",
                null,
                null,
                null
        ), owner);

        ArgumentCaptor<com.todolab.notification.domain.PushNotificationHistory> captor =
                ArgumentCaptor.forClass(com.todolab.notification.domain.PushNotificationHistory.class);
        then(pushNotificationHistoryRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("SERVER:42");
    }

    @Test
    @DisplayName("서버 push 영구 실패 이력 기록은 해당 token을 비활성화한다")
    void recordForOwner_success_deactivatesPermanentFailureToken() {
        PushNotificationHistoryService service = service();
        User owner = new User("push-permanent-failure@example.com", "encoded-password", "Push 실패 사용자");
        ReflectionTestUtils.setField(owner, "id", 10L);
        PushDeviceToken token = new PushDeviceToken(
                owner,
                PushPlatform.EXPO,
                "ExponentPushToken[permanent-failure]",
                "1.0.0",
                "iPhone"
        );
        ReflectionTestUtils.setField(token, "id", 20L);
        given(pushDeviceTokenRepository.findByIdAndOwnerId(20L, 10L)).willReturn(Optional.of(token));
        given(pushNotificationHistoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.recordForOwner(command(
                20L,
                PushNotificationStatus.FAILED,
                42L,
                "task:42",
                "SERVER:42",
                null,
                "DeviceNotRegistered"
        ), owner);

        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("서버 push 일시 실패 이력 기록은 token을 유지한다")
    void recordForOwner_success_keepsTemporaryFailureToken() {
        PushNotificationHistoryService service = service();
        User owner = new User("push-temporary-failure@example.com", "encoded-password", "Push 실패 사용자");
        ReflectionTestUtils.setField(owner, "id", 10L);
        PushDeviceToken token = new PushDeviceToken(
                owner,
                PushPlatform.EXPO,
                "ExponentPushToken[temporary-failure]",
                "1.0.0",
                "iPhone"
        );
        ReflectionTestUtils.setField(token, "id", 20L);
        given(pushDeviceTokenRepository.findByIdAndOwnerId(20L, 10L)).willReturn(Optional.of(token));
        given(pushNotificationHistoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.recordForOwner(command(
                20L,
                PushNotificationStatus.FAILED,
                42L,
                "task:42",
                "SERVER:42",
                null,
                "MessageRateExceeded"
        ), owner);

        assertThat(token.isActive()).isTrue();
    }

    private PushNotificationHistoryRecordCommand command(
            Long pushDeviceTokenId,
            PushNotificationStatus status,
            Long taskId,
            String notificationKey,
            String idempotencyKey,
            String providerMessageId,
            String errorCode
    ) {
        return new PushNotificationHistoryRecordCommand(
                PushNotificationSource.SERVER,
                PushNotificationProvider.EXPO,
                status,
                pushDeviceTokenId,
                taskId,
                null,
                null,
                notificationKey,
                idempotencyKey,
                providerMessageId,
                errorCode,
                null,
                null
        );
    }

    private PushNotificationHistoryService service() {
        return new PushNotificationHistoryService(pushNotificationHistoryRepository, pushDeviceTokenRepository);
    }
}
