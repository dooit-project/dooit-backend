package pj.dooit.workspace.dto;

import pj.dooit.workspace.domain.SharedWorkspace;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공유 workspace 응답")
public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static WorkspaceResponse from(SharedWorkspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getCreatedBy().getId(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt()
        );
    }
}
