package pj.dooit.goal.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.goal-ai")
public class GoalDraftProperties {
    private boolean enabled = false;
    private Duration callTimeout = Duration.ofSeconds(10);
    private int maxConcurrentCalls = 8;
    private int maxRequestsPerWindow = 20;
    private Duration rateLimitWindow = Duration.ofHours(1);
    private int maxTrackedUsers = 10000;

    @AssertTrue(message = "AI call limits are invalid")
    public boolean isLimitsValid() {
        return callTimeout != null && callTimeout.compareTo(Duration.ofMillis(1)) >= 0
                && callTimeout.compareTo(Duration.ofSeconds(60)) <= 0
                && maxConcurrentCalls >= 1 && maxConcurrentCalls <= 64
                && maxRequestsPerWindow >= 1 && maxRequestsPerWindow <= 10000
                && rateLimitWindow != null && rateLimitWindow.compareTo(Duration.ofSeconds(1)) >= 0
                && rateLimitWindow.compareTo(Duration.ofDays(1)) <= 0
                && maxTrackedUsers >= 1 && maxTrackedUsers <= 100000;
    }
}
