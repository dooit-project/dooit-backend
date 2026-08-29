package pj.dooit.auth.scheduler;

import pj.dooit.Constant;
import pj.dooit.auth.service.GuestAccountCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.auth.guest.cleanup.enabled",
        havingValue = "true"
)
public class GuestAccountCleanupScheduler {

    private final GuestAccountCleanupService guestAccountCleanupService;

    @Scheduled(cron = "${app.auth.guest.cleanup.cron:0 30 3 * * *}", zone = Constant.ZONE_ID)
    public void deleteExpiredGuests() {
        try {
            guestAccountCleanupService.deleteExpiredGuests(LocalDateTime.now(Constant.ZONE));
        } catch (Exception e) {
            log.error("[GUEST_CLEANUP] 만료 게스트 정리 실패", e);
        }
    }
}
