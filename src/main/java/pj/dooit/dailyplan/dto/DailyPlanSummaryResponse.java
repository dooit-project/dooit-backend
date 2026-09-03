package pj.dooit.dailyplan.dto;

import pj.dooit.dailyplan.domain.DailyPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "일일 계획 결과 요약 응답")
public record DailyPlanSummaryResponse(
        @Schema(description = "계획 날짜", example = "2026-08-31")
        LocalDate date,
        @Schema(description = "계획 상태", example = "CLOSED")
        DailyPlanStatus status,
        @Schema(description = "계획 확정 시점의 focus Task 수", example = "3")
        int plannedFocusCount,
        @Schema(description = "완료된 focus Task 수", example = "1")
        int completedCount,
        @Schema(description = "다른 날짜 Today로 이동된 focus Task 수", example = "1")
        int movedToOtherDateCount,
        @Schema(description = "Inbox로 이동된 focus Task 수", example = "1")
        int movedToInboxCount,
        @Schema(description = "아직 완료, 이동, Inbox 처리되지 않은 focus Task 수", example = "0")
        int undecidedCount
) {
    public static DailyPlanSummaryResponse empty(LocalDate date) {
        return new DailyPlanSummaryResponse(date, DailyPlanStatus.DRAFT, 0, 0, 0, 0, 0);
    }
}
