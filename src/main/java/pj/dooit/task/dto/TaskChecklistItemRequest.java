package pj.dooit.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Task checklist item 생성/수정 요청")
public record TaskChecklistItemRequest(
        @NotBlank(message = "checklist item 제목은 필수값입니다")
        @Size(max = 30, message = "checklist item 제목은 30자 이하여야 합니다")
        @Schema(description = "checklist item 제목", example = "자료 확인", maxLength = 30)
        String title
) {
}
