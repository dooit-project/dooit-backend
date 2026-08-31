package pj.dooit.dailyplan.dto;

import pj.dooit.dailyplan.domain.DailyPlan;
import pj.dooit.dailyplan.domain.DailyPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "일일 계획 응답")
public record DailyPlanResponse(
        @Schema(description = "계획 날짜", example = "2026-08-31")
        LocalDate date,
        @Schema(description = "계획 상태", example = "CONFIRMED")
        DailyPlanStatus status,
        @Schema(description = "오늘의 핵심 Task ID. 배열 순서가 집중 순서입니다.", example = "[41, 17, 93]")
        List<Long> focusTaskIds,
        @Schema(description = "계획 확정 시각", nullable = true)
        LocalDateTime confirmedAt,
        @Schema(description = "하루 마감 시각", nullable = true)
        LocalDateTime closedAt,
        @Schema(description = "수정 시각. 저장 전 기본 응답에서는 null입니다.", nullable = true)
        LocalDateTime updatedAt
) {
    public static DailyPlanResponse empty(LocalDate date) {
        return new DailyPlanResponse(date, DailyPlanStatus.DRAFT, List.of(), null, null, null);
    }

    public static DailyPlanResponse from(DailyPlan dailyPlan) {
        return new DailyPlanResponse(
                dailyPlan.getDate(),
                dailyPlan.getStatus(),
                List.copyOf(dailyPlan.getFocusTaskIds()),
                dailyPlan.getConfirmedAt(),
                dailyPlan.getClosedAt(),
                dailyPlan.getUpdatedAt()
        );
    }
}
