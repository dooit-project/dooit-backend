package com.todolab.auth.service;

import com.todolab.auth.config.GuestAccountRateLimitProperties;
import com.todolab.auth.config.GuestAccountRateLimitProperties.Store;
import com.todolab.auth.exception.GuestCreationRateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestAccountRateLimiterTest {

    @Test
    @DisplayName("동일 클라이언트 신호의 게스트 생성 요청이 한도를 넘으면 예외를 던진다")
    void assertGuestCreationAllowed_exceedsLimit() {
        GuestAccountRateLimiter rateLimiter = new GuestAccountRateLimiter(
                new GuestAccountRateLimitProperties(true, Store.MEMORY, 2, Duration.ofHours(1), 100),
                new InMemoryGuestAccountRateLimitStore(
                        new GuestAccountRateLimitProperties(true, Store.MEMORY, 2, Duration.ofHours(1), 100)
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        rateLimiter.assertGuestCreationAllowed(request);
        rateLimiter.assertGuestCreationAllowed(request);

        assertThatThrownBy(() -> rateLimiter.assertGuestCreationAllowed(request))
                .isInstanceOf(GuestCreationRateLimitExceededException.class);
    }

    @Test
    @DisplayName("rate limit 비활성화 시 요청 수를 제한하지 않는다")
    void assertGuestCreationAllowed_disabled() {
        GuestAccountRateLimiter rateLimiter = new GuestAccountRateLimiter(
                new GuestAccountRateLimitProperties(false, Store.MEMORY, 1, Duration.ofHours(1), 100),
                new InMemoryGuestAccountRateLimitStore(
                        new GuestAccountRateLimitProperties(false, Store.MEMORY, 1, Duration.ofHours(1), 100)
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        assertThatCode(() -> {
            rateLimiter.assertGuestCreationAllowed(request);
            rateLimiter.assertGuestCreationAllowed(request);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("X-Forwarded-For 첫 IP를 클라이언트 신호로 사용한다")
    void assertGuestCreationAllowed_usesFirstForwardedFor() {
        GuestAccountRateLimiter rateLimiter = new GuestAccountRateLimiter(
                new GuestAccountRateLimitProperties(true, Store.MEMORY, 1, Duration.ofHours(1), 100),
                new InMemoryGuestAccountRateLimitStore(
                        new GuestAccountRateLimitProperties(true, Store.MEMORY, 1, Duration.ofHours(1), 100)
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.20");
        request.setRemoteAddr("198.51.100.99");
        MockHttpServletRequest sameForwardedRequest = new MockHttpServletRequest();
        sameForwardedRequest.addHeader("X-Forwarded-For", "203.0.113.10");
        sameForwardedRequest.setRemoteAddr("198.51.100.100");

        rateLimiter.assertGuestCreationAllowed(request);

        assertThatThrownBy(() -> rateLimiter.assertGuestCreationAllowed(sameForwardedRequest))
                .isInstanceOf(GuestCreationRateLimitExceededException.class);
    }
}
