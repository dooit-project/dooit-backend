package pj.dooit.task.dto;

import pj.dooit.task.domain.query.TaskSearchDateSource;
import pj.dooit.task.domain.query.TaskSearchMatchedField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Task 검색 항목")
public record TaskSearchItemResponse(
        @Schema(description = "Task 본문", requiredMode = Schema.RequiredMode.REQUIRED)
        TaskResponse task,
        @Schema(description = "검색 정렬/필터 기준 날짜. 기준 날짜가 없으면 null입니다.", example = "2026-07-22", nullable = true)
        LocalDate relevantDate,
        @Schema(description = "relevantDate 산출 출처", example = "TARGET_DATE", requiredMode = Schema.RequiredMode.REQUIRED)
        TaskSearchDateSource dateSource,
        @Schema(description = "q가 매칭된 Task 필드. q가 없으면 빈 배열입니다.", example = "[\"TITLE\", \"CATEGORY\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TaskSearchMatchedField> matchedFields,
        @Schema(description = "검색 결과 표시용 짧은 매칭 원문. q가 없으면 null입니다.", example = "출시 회의", nullable = true)
        String highlight
) {
}
