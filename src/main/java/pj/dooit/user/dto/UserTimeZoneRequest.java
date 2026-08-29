package pj.dooit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;

@Schema(description = "사용자 timezone 변경 요청")
public record UserTimeZoneRequest(
        @NotBlank(message = "timeZone은 필수입니다")
        @Size(max = 50, message = "timeZone은 50자 이하여야 합니다")
        @Schema(description = "IANA timezone ID", example = "Asia/Seoul", maxLength = 50)
        String timeZone
) {

    @AssertTrue(message = "timeZone은 유효한 IANA timezone이어야 합니다")
    public boolean isValidTimeZone() {
        if (timeZone == null || timeZone.isBlank()) {
            return true;
        }
        try {
            ZoneId.of(timeZone.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String normalizedTimeZone() {
        return timeZone.trim();
    }
}
