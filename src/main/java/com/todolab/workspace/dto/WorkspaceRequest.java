package com.todolab.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "공유 workspace 생성/수정 요청")
public record WorkspaceRequest(
        @NotBlank(message = "workspace 이름은 필수값입니다")
        @Size(max = 50, message = "workspace 이름은 50자 이하여야 합니다")
        @Schema(description = "workspace 이름", example = "가족 일정", maxLength = 50)
        String name,

        @Size(max = 300, message = "workspace 설명은 300자 이하여야 합니다")
        @Schema(description = "workspace 설명", example = "가족 공유 일정", maxLength = 300, nullable = true)
        String description
) {
}
