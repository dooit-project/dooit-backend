package pj.dooit.dday.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "D-Day 기반 Today Task 생성 요청")
public record DdayGoalTaskRequest(
        @NotBlank(message = "제목은 필수값입니다")
        @Size(max = 30, message = "제목은 30자 이하여야 합니다")
        @Schema(description = "생성할 Today Task 제목", example = "출시 준비", maxLength = 30)
        String title,

        @NotNull(message = "실행 날짜는 필수값입니다")
        @Schema(description = "Today에 배치할 날짜. LocalDate 형식입니다.", example = "2026-07-15", format = "date")
        LocalDate date
) {
}
