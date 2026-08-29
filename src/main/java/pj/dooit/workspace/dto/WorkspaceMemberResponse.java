package pj.dooit.workspace.dto;

import pj.dooit.workspace.domain.WorkspaceMember;
import pj.dooit.workspace.domain.WorkspaceMemberStatus;
import pj.dooit.workspace.domain.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공유 workspace 멤버 응답")
public record WorkspaceMemberResponse(
        Long id,
        Long workspaceId,
        Long userId,
        String email,
        String displayName,
        WorkspaceRole role,
        WorkspaceMemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.getId(),
                member.getWorkspace().getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
