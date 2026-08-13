package com.todolab.workspace.repository;

import com.todolab.workspace.domain.WorkspaceMember;
import com.todolab.workspace.domain.WorkspaceMemberStatus;
import com.todolab.workspace.domain.WorkspaceRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    @EntityGraph(attributePaths = {"workspace", "user"})
    List<WorkspaceMember> findByUserIdAndStatusOrderByIdAsc(Long userId, WorkspaceMemberStatus status);

    @EntityGraph(attributePaths = {"workspace", "user"})
    List<WorkspaceMember> findByWorkspaceIdAndStatusOrderByIdAsc(Long workspaceId, WorkspaceMemberStatus status);

    List<WorkspaceMember> findByWorkspaceIdOrderByIdAsc(Long workspaceId);

    @EntityGraph(attributePaths = {"workspace", "user"})
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    @EntityGraph(attributePaths = {"workspace", "user"})
    Optional<WorkspaceMember> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndRoleAndStatus(Long workspaceId, WorkspaceRole role, WorkspaceMemberStatus status);
}
