package com.todolab.workspace.service;

import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import com.todolab.workspace.domain.SharedWorkspace;
import com.todolab.workspace.domain.WorkspaceMember;
import com.todolab.workspace.domain.WorkspaceMemberStatus;
import com.todolab.workspace.domain.WorkspaceRole;
import com.todolab.workspace.dto.WorkspaceInviteRequest;
import com.todolab.workspace.dto.WorkspaceMemberResponse;
import com.todolab.workspace.dto.WorkspaceMemberUpdateRequest;
import com.todolab.workspace.dto.WorkspaceRequest;
import com.todolab.workspace.dto.WorkspaceResponse;
import com.todolab.workspace.exception.WorkspaceMemberNotFoundException;
import com.todolab.workspace.exception.WorkspaceNotFoundException;
import com.todolab.workspace.exception.WorkspaceValidationException;
import com.todolab.workspace.repository.SharedWorkspaceRepository;
import com.todolab.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final SharedWorkspaceRepository sharedWorkspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkspaceResponse createForOwner(WorkspaceRequest request, User owner) {
        assertRegistered(owner);
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace(
                request.name(),
                request.description(),
                owner
        ));
        workspaceMemberRepository.save(new WorkspaceMember(
                workspace,
                owner,
                WorkspaceRole.OWNER,
                WorkspaceMemberStatus.ACTIVE,
                owner
        ));
        return WorkspaceResponse.from(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findAllForMember(User member) {
        assertRegistered(member);
        return workspaceMemberRepository.findByUserIdAndStatusOrderByIdAsc(member.getId(), WorkspaceMemberStatus.ACTIVE)
                .stream()
                .map(WorkspaceMember::getWorkspace)
                .map(WorkspaceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getForMember(Long id, User member) {
        requireActiveMember(id, member);
        return WorkspaceResponse.from(findWorkspace(id));
    }

    @Transactional
    public WorkspaceResponse updateForOwner(Long id, WorkspaceRequest request, User owner) {
        requireOwner(id, owner);
        SharedWorkspace workspace = findWorkspace(id);
        workspace.update(request.name(), request.description());
        return WorkspaceResponse.from(workspace);
    }

    @Transactional
    public void deleteForOwner(Long id, User owner) {
        requireOwner(id, owner);
        SharedWorkspace workspace = findWorkspace(id);
        workspaceMemberRepository.deleteAll(workspaceMemberRepository.findByWorkspaceIdOrderByIdAsc(id));
        sharedWorkspaceRepository.delete(workspace);
    }

    @Transactional
    public WorkspaceMemberResponse inviteMember(Long workspaceId, WorkspaceInviteRequest request, User inviter) {
        requireOwner(workspaceId, inviter);
        User invitee = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new WorkspaceValidationException("등록된 사용자만 초대할 수 있습니다."));
        assertRegistered(invitee);
        if (invitee.getId().equals(inviter.getId())) {
            throw new WorkspaceValidationException("자기 자신은 초대할 수 없습니다.");
        }
        WorkspaceRole role = request.role() == null ? WorkspaceRole.VIEWER : request.role();
        if (role == WorkspaceRole.OWNER) {
            throw new WorkspaceValidationException("초대 API에서는 OWNER role을 부여할 수 없습니다.");
        }

        SharedWorkspace workspace = findWorkspace(workspaceId);
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, invitee.getId())
                .orElse(null);
        if (member == null) {
            member = new WorkspaceMember(
                    workspace,
                    invitee,
                    role,
                    WorkspaceMemberStatus.PENDING,
                    inviter
            );
            return WorkspaceMemberResponse.from(workspaceMemberRepository.save(member));
        }
        if (member.getStatus() == WorkspaceMemberStatus.ACTIVE || member.getStatus() == WorkspaceMemberStatus.PENDING) {
            throw new WorkspaceValidationException("이미 초대되었거나 참여 중인 사용자입니다.");
        }
        member.changeRole(role);
        member.markPending();
        return WorkspaceMemberResponse.from(workspaceMemberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> findMembers(Long workspaceId, User member) {
        requireActiveMember(workspaceId, member);
        return workspaceMemberRepository.findByWorkspaceIdAndStatusOrderByIdAsc(workspaceId, WorkspaceMemberStatus.ACTIVE)
                .stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse updateMember(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberUpdateRequest request,
            User actor
    ) {
        WorkspaceMember target = findMember(workspaceId, memberId);
        WorkspaceMember actorMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actor.getId())
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));

        if (target.getUser().getId().equals(actor.getId()) && target.getStatus() == WorkspaceMemberStatus.PENDING) {
            if (request.status() != WorkspaceMemberStatus.ACTIVE) {
                throw new WorkspaceValidationException("초대 수락은 ACTIVE 상태만 사용할 수 있습니다.");
            }
            target.activate();
            return WorkspaceMemberResponse.from(target);
        }

        if (actorMember.getStatus() != WorkspaceMemberStatus.ACTIVE || actorMember.getRole() != WorkspaceRole.OWNER) {
            throw new AccessDeniedException("workspace owner 권한이 필요합니다.");
        }
        if (target.getRole() == WorkspaceRole.OWNER && request.role() != null && request.role() != WorkspaceRole.OWNER) {
            ensureAnotherActiveOwner(workspaceId, target.getId());
        }
        if (request.role() != null) {
            if (request.role() == WorkspaceRole.OWNER) {
                throw new WorkspaceValidationException("OWNER 승격은 1차 API에서 지원하지 않습니다.");
            }
            target.changeRole(request.role());
        }
        if (request.status() != null) {
            if (request.status() == WorkspaceMemberStatus.REMOVED) {
                if (target.getRole() == WorkspaceRole.OWNER) {
                    ensureAnotherActiveOwner(workspaceId, target.getId());
                }
                target.remove();
            } else if (request.status() == WorkspaceMemberStatus.ACTIVE) {
                target.activate();
            } else {
                throw new WorkspaceValidationException("PENDING 상태로 되돌릴 수 없습니다.");
            }
        }
        return WorkspaceMemberResponse.from(target);
    }

    @Transactional
    public void removeMember(Long workspaceId, Long memberId, User actor) {
        WorkspaceMember target = findMember(workspaceId, memberId);
        WorkspaceMember actorMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actor.getId())
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        boolean selfLeave = target.getUser().getId().equals(actor.getId());
        boolean ownerRemove = actorMember.getStatus() == WorkspaceMemberStatus.ACTIVE
                && actorMember.getRole() == WorkspaceRole.OWNER;
        if (!selfLeave && !ownerRemove) {
            throw new AccessDeniedException("workspace owner 권한이 필요합니다.");
        }
        if (target.getRole() == WorkspaceRole.OWNER) {
            ensureAnotherActiveOwner(workspaceId, target.getId());
        }
        target.remove();
    }

    private SharedWorkspace findWorkspace(Long id) {
        return sharedWorkspaceRepository.findById(id)
                .orElseThrow(() -> new WorkspaceNotFoundException(id));
    }

    private WorkspaceMember findMember(Long workspaceId, Long memberId) {
        return workspaceMemberRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(memberId));
    }

    private WorkspaceMember requireActiveMember(Long workspaceId, User user) {
        assertRegistered(user);
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        if (member.getStatus() != WorkspaceMemberStatus.ACTIVE) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
        return member;
    }

    private WorkspaceMember requireOwner(Long workspaceId, User user) {
        WorkspaceMember member = requireActiveMember(workspaceId, user);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new AccessDeniedException("workspace owner 권한이 필요합니다.");
        }
        return member;
    }

    private void ensureAnotherActiveOwner(Long workspaceId, Long excludedMemberId) {
        boolean hasOwner = workspaceMemberRepository.findByWorkspaceIdAndStatusOrderByIdAsc(
                        workspaceId,
                        WorkspaceMemberStatus.ACTIVE
                )
                .stream()
                .anyMatch(member -> member.getRole() == WorkspaceRole.OWNER && !member.getId().equals(excludedMemberId));
        if (!hasOwner) {
            throw new WorkspaceValidationException("workspace에는 최소 1명의 OWNER가 필요합니다.");
        }
    }

    private void assertRegistered(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user는 영속화된 사용자여야 합니다.");
        }
        if (user.getAccountType() == AccountType.GUEST) {
            throw new AccessDeniedException("게스트 계정은 workspace 기능을 사용할 수 없습니다.");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
