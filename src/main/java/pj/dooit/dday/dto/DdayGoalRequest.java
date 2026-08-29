package pj.dooit.dday.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "D-Day 목표 생성 요청")
public record DdayGoalRequest(
        @NotBlank
        @Size(max = 50)
        @Schema(description = "D-Day 목표 제목", example = "출시", maxLength = 50)
        String title,

        @NotNull
        @Schema(description = "목표 날짜. LocalDate 형식입니다.", example = "2026-08-01", format = "date")
        LocalDate targetDate
) {
}
