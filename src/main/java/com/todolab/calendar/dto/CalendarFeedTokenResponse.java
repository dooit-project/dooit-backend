package com.todolab.calendar.dto;

import com.todolab.calendar.domain.CalendarFeedToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "iCalendar feed token 응답")
public record CalendarFeedTokenResponse(
        @Schema(description = "이번 응답에서만 확인 가능한 원본 feed token", example = "url-safe-token")
        String token,
        @Schema(description = "feed 구독 path", example = "/api/v1/calendar-feeds/url-safe-token.ics")
        String feedPath,
        @Schema(description = "token 생성 시각", example = "2026-08-27T13:00:00")
        LocalDateTime createdAt,
        @Schema(description = "활성 여부", example = "true")
        boolean active
) {

    public static CalendarFeedTokenResponse issued(CalendarFeedToken token, String rawToken) {
        return new CalendarFeedTokenResponse(
                rawToken,
                "/api/v1/calendar-feeds/%s.ics".formatted(rawToken),
                token.getCreatedAt(),
                token.isActive()
        );
    }
}
