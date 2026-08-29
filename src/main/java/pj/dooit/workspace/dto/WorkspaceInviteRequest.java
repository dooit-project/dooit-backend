package pj.dooit.workspace.dto;

import pj.dooit.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "공유 workspace 멤버 초대 요청")
public record WorkspaceInviteRequest(
        @NotBlank(message = "초대 email은 필수값입니다")
        @Email(message = "email 형식이 올바르지 않습니다")
        @Schema(description = "초대할 등록 사용자 email", example = "member@example.com")
        String email,

        @Schema(description = "부여할 role. OWNER는 초대 API에서 부여할 수 없습니다.", example = "EDITOR")
        WorkspaceRole role
) {
}
