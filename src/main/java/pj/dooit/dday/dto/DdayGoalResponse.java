package pj.dooit.dday.dto;

import pj.dooit.Constant;
import pj.dooit.dday.domain.DdayGoal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Schema(description = "D-Day 목표 응답")
public record DdayGoalResponse(
        @Schema(description = "D-Day 목표 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "D-Day 목표 제목", example = "출시", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "목표 날짜", example = "2026-08-01", format = "date", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate targetDate,
        @Schema(description = "오늘 기준 목표일까지 남은 일수. 지난 날짜면 음수입니다.", example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
        long daysLeft,
        @Schema(description = "생성 시각", example = "2026-07-15T09:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt
) {

    public static DdayGoalResponse from(DdayGoal goal) {
        return new DdayGoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getTargetDate(),
                ChronoUnit.DAYS.between(LocalDate.now(Constant.ZONE), goal.getTargetDate()),
                goal.getCreatedAt()
        );
    }
}
