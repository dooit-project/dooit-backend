package pj.dooit.dailyplan.dto;

import pj.dooit.dailyplan.domain.DailyPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "일일 계획 저장 요청")
public record DailyPlanRequest(
        @Schema(description = "오늘의 핵심 Task ID. 최대 3개이며 배열 순서를 보존합니다.", example = "[41, 17, 93]")
        List<Long> focusTaskIds,
        @Schema(description = "계획 상태", example = "CONFIRMED")
        DailyPlanStatus status
) {
}
