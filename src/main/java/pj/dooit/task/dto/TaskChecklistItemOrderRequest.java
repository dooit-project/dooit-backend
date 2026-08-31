package pj.dooit.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Task checklist item 일괄 재정렬 요청")
public record TaskChecklistItemOrderRequest(
        @Schema(description = "현재 Task의 checklist item ID 전체 목록. 배열 순서가 저장 순서입니다.", example = "[3, 1, 2]")
        List<Long> orderedItemIds
) {
}
