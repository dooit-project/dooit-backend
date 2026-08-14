package com.todolab.workspace.dto;

import com.todolab.workspace.domain.WorkspaceMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "현재 사용자 workspace 초대 응답")
public record WorkspaceInvitationResponse(
        WorkspaceResponse workspace,
        WorkspaceMemberResponse membership,
        LocalDateTime invitedAt
) {

    public static WorkspaceInvitationResponse from(WorkspaceMember member) {
        return new WorkspaceInvitationResponse(
                WorkspaceResponse.from(member.getWorkspace()),
                WorkspaceMemberResponse.from(member),
                member.getCreatedAt()
        );
    }
}
