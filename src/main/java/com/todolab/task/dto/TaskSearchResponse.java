package com.todolab.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Task 검색 응답")
public record TaskSearchResponse(
        @Schema(description = "검색 결과 항목", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TaskSearchItemResponse> items,
        @Schema(description = "다음 페이지 커서. 마지막 항목의 Task id이며 다음 페이지가 없으면 null입니다.", example = "42", nullable = true)
        String nextCursor,
        @Schema(description = "요청에 적용된 페이지 크기", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(description = "빈 검색 결과에서 모바일이 다시 검색어로 제안할 수 있는 category 기반 query", example = "[\"업무\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> suggestedQueries,
        @Schema(description = "빈 검색 결과에서 모바일이 category 필터로 제안할 수 있는 category", example = "[\"업무\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> suggestedCategories
) {
}
