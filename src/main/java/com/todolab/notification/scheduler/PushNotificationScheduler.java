package com.todolab.notification.scheduler;

import com.todolab.notification.service.PushNotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.notification.push.enabled",
        havingValue = "true"
)
public class PushNotificationScheduler {

    private final PushNotificationDispatchService pushNotificationDispatchService;

    @Scheduled(fixedDelayString = "${app.notification.push.scheduler-fixed-delay:PT1M}")
    public void dispatchDueNotifications() {
        try {
            int sentCount = pushNotificationDispatchService.dispatchDueNotifications();
            if (sentCount > 0) {
                log.info("[PUSH] 서버 push 발송 완료. sentCount={}", sentCount);
            }
        } catch (Exception e) {
            log.error("[PUSH] 서버 push 발송 실패", e);
        }
    }
}
