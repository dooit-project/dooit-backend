package pj.dooit.goal.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pj.dooit.goal.exception.GoalAiRateLimitExceededException;
import pj.dooit.goal.exception.GoalAiUnavailableException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/** Process-local admission and waiting limits. Provider adapters must also enforce HTTP timeouts. */
@Component
public class GoalAiCallExecutor {
    private final ThreadPoolExecutor workers;
    private final Map<Long, Window> windows = new HashMap<>();
    private final LongSupplier nanoTime;
    private final long timeoutNanos;
    private final long windowNanos;
    private final int maxRequests;
    private final int maxTrackedUsers;

    @Autowired
    public GoalAiCallExecutor(GoalDraftProperties properties) {
        this(properties, System::nanoTime);
    }

    GoalAiCallExecutor(GoalDraftProperties properties, LongSupplier nanoTime) {
        if (!properties.isLimitsValid()) throw new IllegalArgumentException("Invalid goal AI limits");
        this.nanoTime = nanoTime;
        this.timeoutNanos = properties.getCallTimeout().toNanos();
        this.windowNanos = properties.getRateLimitWindow().toNanos();
        this.maxRequests = properties.getMaxRequestsPerWindow();
        this.maxTrackedUsers = properties.getMaxTrackedUsers();
        int concurrency = properties.getMaxConcurrentCalls();
        workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().name("goal-ai-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T call(Long ownerId, Callable<T> providerCall) {
        if (ownerId == null || ownerId <= 0) throw new IllegalArgumentException("Persisted owner required");
        Future<T> future;
        synchronized (windows) {
            long now = nanoTime.getAsLong();
            Window window = windows.get(ownerId);
            if (window != null && now - window.startedAt() >= windowNanos) {
                windows.remove(ownerId);
                window = null;
            }
            if (window != null && window.count() >= maxRequests) {
                long remaining = windowNanos - (now - window.startedAt());
                throw new GoalAiRateLimitExceededException(Math.max(1, (remaining + 999999999L) / 1000000000L));
            }
            if (window == null && windows.size() >= maxTrackedUsers) {
                windows.values().removeIf(value -> now - value.startedAt() >= windowNanos);
                if (windows.size() >= maxTrackedUsers) throw new GoalAiUnavailableException();
            }
            try {
                // No queue: timed-out providers that ignore interruption keep occupying their worker.
                future = workers.submit(providerCall);
            } catch (RejectedExecutionException ignored) {
                throw new GoalAiUnavailableException();
            }
            windows.put(ownerId, window == null ? new Window(now, 1) : new Window(window.startedAt(), window.count() + 1));
        }
        try {
            return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GoalAiUnavailableException();
        } catch (TimeoutException ignored) {
            future.cancel(true);
            throw new GoalAiUnavailableException();
        } catch (ExecutionException ignored) {
            // Do not retain provider exception details or causes.
            throw new GoalAiUnavailableException();
        }
    }

    @PreDestroy
    public void shutdown() {
        workers.shutdownNow();
    }

    private record Window(long startedAt, int count) {}
}
