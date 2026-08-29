package pj.dooit.notification.controller;

import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.domain.PushDeviceToken;
import pj.dooit.notification.domain.PushNotificationSource;
import pj.dooit.notification.domain.PushNotificationStatus;
import pj.dooit.notification.domain.PushPlatform;
import pj.dooit.notification.repository.PushDeviceTokenRepository;
import pj.dooit.notification.service.PushNotificationHistoryRecordCommand;
import pj.dooit.notification.service.PushNotificationHistoryService;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushNotificationHistoryV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PushDeviceTokenRepository pushDeviceTokenRepository;

    @Autowired
    PushNotificationHistoryService pushNotificationHistoryService;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 push 알림 전송 이력은 owner 범위에서 최신순 조회하고 token 전체를 숨긴다")
    void pushNotificationHistory_list_ownerScoped() throws Exception {
        User owner = userRepository.save(new User("push-history-owner@example.com", "encoded-password", "Push 이력 사용자"));
        User other = userRepository.save(new User("push-history-other@example.com", "encoded-password", "다른 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        PushDeviceToken token = pushDeviceTokenRepository.save(new PushDeviceToken(
                owner,
                PushPlatform.EXPO,
                "owner-token-123456",
                "1.0.0",
                "owner iPhone"
        ));
        PushDeviceToken otherToken = pushDeviceTokenRepository.save(new PushDeviceToken(
                other,
                PushPlatform.EXPO,
                "other-token-654321",
                "1.0.0",
                "other iPhone"
        ));

        pushNotificationHistoryService.recordForOwner(command(
                token.getId(),
                PushNotificationStatus.SUCCESS,
                10L,
                "task:10",
                "SERVER:10",
                "ticket-10",
                null,
                null,
                LocalDateTime.of(2026, 7, 30, 9, 0)
        ), owner);
        pushNotificationHistoryService.recordForOwner(command(
                token.getId(),
                PushNotificationStatus.FAILED,
                11L,
                "task:11",
                "SERVER:11",
                null,
                "DeviceNotRegistered",
                "Device not registered",
                LocalDateTime.of(2026, 7, 30, 10, 0)
        ), owner);
        pushNotificationHistoryService.recordForOwner(command(
                otherToken.getId(),
                PushNotificationStatus.SUCCESS,
                99L,
                "task:99",
                "SERVER:99",
                "ticket-99",
                null,
                null,
                LocalDateTime.of(2026, 7, 30, 11, 0)
        ), other);

        String response = mockMvc.perform(get("/api/v1/push-notification-histories")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].tokenSuffix").value("123456"))
                .andExpect(jsonPath("$.data[0].errorCode").value("DeviceNotRegistered"))
                .andExpect(jsonPath("$.data[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[1].providerMessageId").value("ticket-10"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .contains("SERVER:10")
                .doesNotContain("owner-token-123456")
                .doesNotContain("SERVER:99");
        assertThat(pushNotificationHistoryService.hasSuccessfulHistory(owner.getId(), "SERVER:10")).isTrue();
        assertThat(pushNotificationHistoryService.hasSuccessfulHistory(owner.getId(), "SERVER:11")).isFalse();
    }

    private PushNotificationHistoryRecordCommand command(
            Long pushDeviceTokenId,
            PushNotificationStatus status,
            Long taskId,
            String notificationKey,
            String idempotencyKey,
            String providerMessageId,
            String errorCode,
            String errorMessage,
            LocalDateTime attemptedAt
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
                errorMessage,
                attemptedAt
        );
    }
}
