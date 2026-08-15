package com.todolab.workspace.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.user.domain.User;
import com.todolab.workspace.dto.WorkspaceInviteRequest;
import com.todolab.workspace.dto.WorkspaceMemberResponse;
import com.todolab.workspace.dto.WorkspaceMemberUpdateRequest;
import com.todolab.workspace.dto.WorkspaceRequest;
import com.todolab.workspace.dto.WorkspaceResponse;
import com.todolab.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "v1 Workspace", description = "공유 workspace API")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceV1Controller {

    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Workspace 생성", description = "로그인 사용자의 공유 workspace를 생성하고 생성자를 OWNER로 등록합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkspaceRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(workspaceService.createForOwner(request, owner)));
    }

    @Operation(summary = "Workspace 목록 조회", description = "로그인 사용자가 ACTIVE 멤버인 workspace 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        User member = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.findAllForMember(member)));
    }

    @Operation(summary = "Workspace 단건 조회", description = "로그인 사용자가 ACTIVE 멤버인 workspace를 단건 조회합니다.")
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User member = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.getForMember(workspaceId, member)));
    }

    @Operation(summary = "Workspace 수정", description = "OWNER가 workspace 이름과 설명을 수정합니다.")
    @PutMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.updateForOwner(workspaceId, request, owner)));
    }

    @Operation(summary = "Workspace 삭제", description = "OWNER가 workspace를 삭제합니다. Task 공유 도입 전 기본 workspace만 대상으로 합니다.")
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User owner = currentUserService.requireUser(jwt);
        workspaceService.deleteForOwner(workspaceId, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Workspace 멤버 초대", description = "OWNER가 등록 사용자를 PENDING 멤버로 초대합니다.")
    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> inviteMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(workspaceService.inviteMember(workspaceId, request, owner)));
    }

    @Operation(summary = "Workspace 멤버 목록 조회", description = "ACTIVE 멤버가 workspace의 ACTIVE 멤버 목록을 조회합니다.")
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberResponse>>> findMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User member = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.findMembers(workspaceId, member)));
    }

    @Operation(summary = "Workspace 멤버 수정", description = "OWNER가 role/status를 변경하거나 초대받은 사용자가 자기 초대를 수락합니다.")
    @PatchMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> updateMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody WorkspaceMemberUpdateRequest request
    ) {
        User actor = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.updateMember(workspaceId, memberId, request, actor)));
    }

    @Operation(summary = "Workspace 멤버 제거", description = "OWNER가 멤버를 제거하거나 멤버가 본인 membership에서 나갑니다.")
    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long memberId
    ) {
        User actor = currentUserService.requireUser(jwt);
        workspaceService.removeMember(workspaceId, memberId, actor);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
