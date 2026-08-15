package com.todolab.workspace.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.user.domain.User;
import com.todolab.workspace.dto.WorkspaceInvitationResponse;
import com.todolab.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace-invitations")
@RequiredArgsConstructor
@Tag(name = "v1 Workspace Invitation", description = "현재 사용자 workspace 초대 API")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceInvitationV1Controller {

    private final WorkspaceService workspaceService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "현재 사용자 Workspace 초대 목록", description = "로그인 사용자의 PENDING workspace membership만 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> findInvitations(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User member = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(workspaceService.findPendingInvitationsForMember(member)));
    }
}
