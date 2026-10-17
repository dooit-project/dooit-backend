package pj.dooit.goal.ai;

import org.junit.jupiter.api.Test;
import pj.dooit.goal.exception.GoalAiRateLimitExceededException;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class GoalAiCallExecutorTest {
    @Test void accountWindowIsSharedAndResetsWithoutAffectingOtherAccounts() {
        var properties = new GoalDraftProperties();
        properties.setMaxRequestsPerWindow(2);
        properties.setRateLimitWindow(Duration.ofSeconds(1));
        var time = new AtomicLong();
        var executor = new GoalAiCallExecutor(properties, time::get);
        try {
            assertThat(executor.call(1L, () -> "first")).isEqualTo("first");
            executor.call(1L, () -> "second");
            assertThatThrownBy(() -> executor.call(1L, () -> "never"))
                    .isInstanceOfSatisfying(GoalAiRateLimitExceededException.class,
                            e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1));
            assertThat(executor.call(2L, () -> "other")).isEqualTo("other");
            time.set(Duration.ofSeconds(1).toNanos());
            assertThat(executor.call(1L, () -> "new window")).isEqualTo("new window");
        } finally { executor.shutdown(); }
    }

    @Test void trackingCapacityDoesNotEvictActiveQuotasAndExpiredEntriesAreReclaimed() {
        var properties = new GoalDraftProperties();
        properties.setMaxTrackedUsers(1);
        properties.setMaxRequestsPerWindow(1);
        properties.setRateLimitWindow(Duration.ofSeconds(1));
        var time = new AtomicLong();
        var executor = new GoalAiCallExecutor(properties, time::get);
        try {
            executor.call(1L, () -> "ok");
            assertThatThrownBy(() -> executor.call(2L, () -> "never")).isInstanceOf(GoalAiUnavailableException.class);
            assertThatThrownBy(() -> executor.call(1L, () -> "never")).isInstanceOf(GoalAiRateLimitExceededException.class);
            time.set(Duration.ofSeconds(1).toNanos());
            assertThat(executor.call(2L, () -> "reclaimed")).isEqualTo("reclaimed");
        } finally { executor.shutdown(); }
    }

    @Test void providerFailureConsumesAttemptAndDiscardsSensitiveCause() {
        var properties = new GoalDraftProperties();
        properties.setMaxRequestsPerWindow(1);
        var executor = new GoalAiCallExecutor(properties);
        try {
            assertThatThrownBy(() -> executor.call(1L, () -> { throw new IllegalStateException("private-credential"); }))
                    .isInstanceOf(GoalAiUnavailableException.class).hasNoCause().hasMessageNotContaining("private");
            assertThatThrownBy(() -> executor.call(1L, () -> "never")).isInstanceOf(GoalAiRateLimitExceededException.class);
        } finally { executor.shutdown(); }
    }

    @Test void timedOutProviderThatIgnoresInterruptStillOccupiesCapacityAndRejectedAttemptIsNotCharged() throws Exception {
        var properties = new GoalDraftProperties();
        properties.setCallTimeout(Duration.ofMillis(100));
        properties.setMaxConcurrentCalls(1);
        properties.setMaxRequestsPerWindow(1);
        var executor = new GoalAiCallExecutor(properties);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var interruptions = new AtomicInteger();
        try {
            assertThatThrownBy(() -> executor.call(1L, () -> {
                try {
                    while (true) {
                        try { release.await(); return "late result"; }
                        catch (InterruptedException ignored) { interruptions.incrementAndGet(); }
                    }
                } finally { finished.countDown(); }
            })).isInstanceOf(GoalAiUnavailableException.class).hasNoCause();
            assertThatThrownBy(() -> executor.call(2L, () -> "never")).isInstanceOf(GoalAiUnavailableException.class);
            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            boolean recovered = false;
            while (System.nanoTime() < until) {
                try {
                    assertThat(executor.call(2L, () -> "recovered")).isEqualTo("recovered");
                    recovered = true;
                    break;
                } catch (GoalAiUnavailableException ignored) { Thread.onSpinWait(); }
            }
            assertThat(recovered).isTrue();
            assertThat(interruptions.get()).isPositive();
        } finally { release.countDown(); executor.shutdown(); }
    }

    @Test void callerInterruptionIsPreservedAndShutdownRejectsWithoutCallingProvider() {
        var executor = new GoalAiCallExecutor(new GoalDraftProperties());
        var release = new CountDownLatch(1);
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> executor.call(1L, () -> { release.await(); return "never"; }))
                    .isInstanceOf(GoalAiUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); release.countDown(); executor.shutdown(); }
        assertThatThrownBy(() -> executor.call(2L, () -> "never")).isInstanceOf(GoalAiUnavailableException.class);
    }

    @Test void invalidLimitConfigurationFailsClosed() {
        var properties = new GoalDraftProperties();
        properties.setCallTimeout(Duration.ZERO);
        assertThatThrownBy(() -> new GoalAiCallExecutor(properties)).isInstanceOf(IllegalArgumentException.class);
        properties.setCallTimeout(Duration.ofSeconds(61));
        assertThat(properties.isLimitsValid()).isFalse();
        properties.setCallTimeout(Duration.ofSeconds(10));
        properties.setMaxConcurrentCalls(0);
        assertThat(properties.isLimitsValid()).isFalse();
    }
}
