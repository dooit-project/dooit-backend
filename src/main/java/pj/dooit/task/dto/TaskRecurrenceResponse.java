package pj.dooit.task.dto;

import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "반복 series 응답")
public record TaskRecurrenceResponse(
        @Schema(description = "반복 series ID", example = "1")
        Long id,
        @Schema(description = "반복 주기", example = "WEEKLY")
        RecurrenceFrequency frequency,
        @Schema(description = "반복 간격", example = "1")
        int interval,
        @Schema(description = "정규화된 RRULE", example = "FREQ=WEEKLY;INTERVAL=1;BYDAY=TU;COUNT=10")
        String recurrenceRule,
        @Schema(description = "반복 계산 기준 timezone", example = "Asia/Seoul")
        String timeZone,
        @Schema(description = "반복 시작 일시", example = "2026-07-07T09:00:00")
        LocalDateTime recurrenceStartAt,
        @Schema(description = "반복 종료일", example = "2026-12-31", nullable = true)
        LocalDate recurrenceUntil,
        @Schema(description = "반복 횟수", example = "10", nullable = true)
        Integer recurrenceCount
) {

    static TaskRecurrenceResponse from(RecurrenceSeries series) {
        if (series == null) {
            return null;
        }
        return new TaskRecurrenceResponse(
                series.getId(),
                series.getFrequency(),
                series.getInterval(),
                series.getRecurrenceRule(),
                series.getTimeZone(),
                series.getRecurrenceStartAt(),
                series.getRecurrenceUntil(),
                series.getRecurrenceCount()
        );
    }
}
