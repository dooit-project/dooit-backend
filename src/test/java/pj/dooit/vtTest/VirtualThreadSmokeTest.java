package pj.dooit.vtTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class VirtualThreadSmokeTest {

    @Test
    void vthread_vs_platformthread_sleep_benchmark() throws Exception {
        int tasks = 2000;
        int sleepMs = 100;

        Duration vthread = runWithExecutor(Executors.newVirtualThreadPerTaskExecutor(), tasks, sleepMs);
        Duration platform = runWithExecutor(Executors.newFixedThreadPool(200), tasks, sleepMs);

        System.out.println("[vthread]   " + vthread.toMillis() + " ms");
        System.out.println("[platform]  " + platform.toMillis() + " ms");

        assertThat(vthread).isNotNull();
        assertThat(platform).isNotNull();
    }

    private Duration runWithExecutor(ExecutorService executor, int tasks, int sleepMs) throws Exception {
        long start = System.nanoTime();
        try (executor) {
            List<Future<?>> futures = new ArrayList<>(tasks);

            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        Thread.sleep(sleepMs); // 블로킹 시뮬레이션
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }

            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        }
        long end = System.nanoTime();
        return Duration.ofNanos(end - start);
    }
}
