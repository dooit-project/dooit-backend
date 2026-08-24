package com.todolab.workspace.dto;

import com.todolab.workspace.domain.WorkspaceMemberStatus;
import com.todolab.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유 workspace 멤버 수정 요청")
public record WorkspaceMemberUpdateRequest(
        @Schema(description = "변경할 role. OWNER는 1차 API에서 승격 대상으로 지원하지 않습니다.", example = "VIEWER", nullable = true)
        WorkspaceRole role,

        @Schema(description = "변경할 멤버십 상태. 초대 수락은 ACTIVE, 초대 거절은 REMOVED를 보냅니다.", example = "ACTIVE", nullable = true)
        WorkspaceMemberStatus status
) {
}
