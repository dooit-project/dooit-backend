package com.todolab.task.dto;

import com.todolab.Constant;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.RecurrenceRuleValidator;
import com.todolab.task.exception.TaskValidationException;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Schema(description = "반복 Task 생성 요청")
public record TaskRecurrenceRequest(
        @Schema(description = "반복 주기", example = "WEEKLY", allowableValues = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"})
        RecurrenceFrequency frequency,

        @Schema(description = "반복 간격. 생략하면 1입니다.", example = "1", nullable = true, minimum = "1")
        Integer interval,

        @Schema(description = "직접 지정 RRULE. 생략하면 frequency/interval/byDays/byMonthDays/count/until로 생성합니다.", example = "FREQ=WEEKLY;INTERVAL=1;BYDAY=TU", nullable = true)
        String recurrenceRule,

        @Schema(description = "반복 계산 기준 IANA timezone. 생략하면 Asia/Seoul입니다.", example = "Asia/Seoul", nullable = true)
        String timeZone,

        @Schema(description = "반복 종료일. recurrenceCount와 함께 사용할 수 없습니다.", type = "string", format = "date", example = "2026-12-31", nullable = true)
        LocalDate recurrenceUntil,

        @Schema(description = "반복 횟수. recurrenceUntil과 함께 사용할 수 없습니다.", example = "10", nullable = true, minimum = "1")
        Integer recurrenceCount,

        @Schema(description = "요일 반복 값. WEEKLY에서 사용합니다.", example = "[\"TU\"]", nullable = true)
        List<String> byDays,

        @Schema(description = "월 반복 일자. MONTHLY에서 사용합니다. -1은 월말입니다.", example = "[15]", nullable = true)
        List<Integer> byMonthDays
) {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public void validate(LocalDateTime taskStartAt) {
        if (taskStartAt == null) {
            throw new TaskValidationException("반복 Task는 시작 일시가 필요합니다.");
        }

        try {
            RecurrenceRuleValidator.validate(
                    normalizedFrequency(),
                    normalizedInterval(),
                    normalizedRecurrenceRule(),
                    normalizedTimeZone(),
                    recurrenceUntil,
                    recurrenceCount
            );
        } catch (IllegalArgumentException e) {
            throw new TaskValidationException(e.getMessage());
        }
    }

    public RecurrenceFrequency normalizedFrequency() {
        if (frequency == null) {
            throw new TaskValidationException("반복 주기는 필수입니다.");
        }
        return frequency;
    }

    public int normalizedInterval() {
        return interval == null ? 1 : interval;
    }

    public String normalizedTimeZone() {
        if (timeZone == null || timeZone.isBlank()) {
            return Constant.ZONE.getId();
        }
        return timeZone.trim();
    }

    public String normalizedRecurrenceRule() {
        if (recurrenceRule != null && !recurrenceRule.isBlank()) {
            return recurrenceRule.trim();
        }

        StringBuilder rule = new StringBuilder()
                .append("FREQ=")
                .append(normalizedFrequency().name())
                .append(";INTERVAL=")
                .append(normalizedInterval());

        if (byDays != null && !byDays.isEmpty()) {
            rule.append(";BYDAY=").append(normalizedByDays());
        }
        if (byMonthDays != null && !byMonthDays.isEmpty()) {
            rule.append(";BYMONTHDAY=").append(normalizedByMonthDays());
        }
        if (recurrenceUntil != null) {
            rule.append(";UNTIL=").append(BASIC_DATE.format(recurrenceUntil));
        }
        if (recurrenceCount != null) {
            rule.append(";COUNT=").append(recurrenceCount);
        }

        return rule.toString();
    }

    private String normalizedByDays() {
        return byDays.stream()
                .map(day -> day == null ? "" : day.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    private String normalizedByMonthDays() {
        return byMonthDays.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
