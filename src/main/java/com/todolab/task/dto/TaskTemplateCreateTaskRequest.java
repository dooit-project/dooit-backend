package com.todolab.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Task 템플릿 기반 Task 생성 요청")
public record TaskTemplateCreateTaskRequest(
        @Schema(description = "Task 생성 날짜. 일정 또는 반복 템플릿에서는 필수입니다.", type = "string", format = "date", example = "2026-08-17", nullable = true)
        LocalDate targetDate,

        @Size(max = 30, message = "제목 override는 30자 이하여야 합니다")
        @Schema(description = "템플릿 제목 대신 사용할 제목", example = "월요일 운동", maxLength = 30, nullable = true)
        String title,

        @Size(max = 300, message = "설명 override는 300자 이하여야 합니다")
        @Schema(description = "템플릿 설명 대신 사용할 설명", maxLength = 300, nullable = true)
        String description,

        @Size(max = 30, message = "카테고리 override는 30자 이하여야 합니다")
        @Schema(description = "템플릿 카테고리 대신 사용할 카테고리", example = "건강", maxLength = 30, nullable = true)
        String category,

        @Schema(description = "생성된 Task에 연결할 개인 D-Day 목표 ID. 생략하면 연결하지 않습니다.", example = "1", nullable = true)
        Long ddayGoalId
) {
}
