package pj.dooit.common.idempotency;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import pj.dooit.config.CorsConfig;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class IdempotencyConcurrencyTest {

    @Test
    void failedRequestDoesNotSplitLockBetweenWaitingAndNewRequests() throws Exception {
        var beans = new StaticListableBeanFactory();
        beans.addBean("repository", mock(IdempotencyRecordRepository.class));
        var filter = new IdempotencyFilter(beans.getBeanProvider(IdempotencyRecordRepository.class),
                beans.getBeanProvider(JwtDecoder.class));
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        var thirdEntered = new CountDownLatch(1);
        var calls = new AtomicInteger();
        FilterChain chain = (request, response) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                awaitLatch(releaseFirst);
            } else if (call == 2) {
                secondEntered.countDown();
                awaitLatch(releaseSecond);
            } else {
                thirdEntered.countDown();
            }
            // 실패 응답은 저장하지 않으므로 대기 요청도 실제 처리를 다시 시도한다.
            ((jakarta.servlet.http.HttpServletResponse) response).setStatus(400);
        };
        FutureTask<Void> first = request(filter, chain);
        FutureTask<Void> second = request(filter, chain);
        FutureTask<Void> third = request(filter, chain);
        Thread firstThread = Thread.ofPlatform().start(first);
        Thread secondThread = null;
        Thread thirdThread = null;
        try {
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            secondThread = Thread.ofPlatform().start(second);
            Thread waiting = secondThread;
            await().atMost(Duration.ofSeconds(5)).until(() -> waiting.getState() == Thread.State.BLOCKED);
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
            thirdThread = Thread.ofPlatform().start(third);
            assertThat(thirdEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            firstThread.join(5000);
            if (secondThread != null) secondThread.join(5000);
            if (thirdThread != null) thirdThread.join(5000);
        }
        second.get(5, TimeUnit.SECONDS);
        third.get(5, TimeUnit.SECONDS);
        assertThat(calls.get()).isEqualTo(3);
    }

    private FutureTask<Void> request(IdempotencyFilter filter, FilterChain chain) {
        return new FutureTask<>(() -> {
            var request = new MockHttpServletRequest("POST", "/api/v1/tasks");
            request.addHeader(CorsConfig.IDEMPOTENCY_KEY_HEADER, "same-key");
            request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            return null;
        });
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("요청 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
